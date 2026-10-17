const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const {JSDOM} = require("jsdom");
const createDOMPurify = require("dompurify");
const {marked} = require("marked");

const root = path.resolve(__dirname, "../../..");
const html = fs.readFileSync(path.join(root, "src/main/resources/static/index.html"), "utf8");
const script = fs.readFileSync(path.join(root, "src/main/resources/static/app.js"), "utf8");
const styles = fs.readFileSync(path.join(root, "src/main/resources/static/styles.css"), "utf8");
const offer = {
  id: "offer-1",
  jobName: "Backend Engineer",
  jobDescription: "Build backend services",
  requiredQualifications: "Five years of Java experience",
  location: "Dublin",
  allocation: "Remote",
  responsibilities: "Lead platform work",
  benefits: "Health coverage",
  companyName: "Example Corp",
  niceToHave: "Kubernetes experience",
  compensation: "Competitive salary",
};

async function loadPage(fetchImplementation) {
  const dom = new JSDOM(html, {runScripts: "outside-only", url: "http://localhost/"});
  dom.window.fetch = fetchImplementation;
  dom.window.marked = marked;
  dom.window.DOMPurify = createDOMPurify(dom.window);
  dom.window.eval(script);
  await new Promise((resolve) => setImmediate(resolve));
  return dom;
}

function response(body, ok = true) {
  return {ok, json: async () => body};
}

function fillForm(document, values = offer) {
  document.querySelector("#job-name").value = values.jobName;
  document.querySelector("#job-description").value = values.jobDescription;
  document.querySelector("#job-qualifications").value = values.requiredQualifications;
  document.querySelector("#job-location").value = values.location;
  document.querySelector("#job-allocation").value = values.allocation;
  document.querySelector("#responsibilities").value = values.responsibilities;
  document.querySelector("#benefits").value = values.benefits;
  document.querySelector("#company-name").value = values.companyName;
  document.querySelector("#nice-to-have").value = values.niceToHave;
  document.querySelector("#compensation").value = values.compensation;
}

async function submitForm(dom) {
  dom.window.document.querySelector("#offer-form").dispatchEvent(
      new dom.window.Event("submit", {bubbles: true, cancelable: true}),
  );
  await new Promise((resolve) => setImmediate(resolve));
}

test("loads offers, selects details, and shows management controls appropriately", async () => {
  const dom = await loadPage(async () => response([offer]));
  const {document} = dom.window;

  assert.equal(document.querySelectorAll(".offer-button").length, 1);
  assert.equal(document.querySelector(".offer-company").textContent, offer.companyName);
  assert.equal(document.querySelector("#edit-offer").classList.contains("d-none"), true);
  assert.equal(document.querySelector("#new-offer").textContent, "New Job Offer");
  const mainChildren = [...document.querySelector("main").children];
  assert.equal(mainChildren.indexOf(document.querySelector(".action-panel")) + 1,
      mainChildren.indexOf(document.querySelector(".workspace")));
  document.querySelector(".offer-button").click();
  assert.equal(document.querySelector("#detail-name").textContent, offer.jobName);
  assert.equal(document.querySelector("#edit-offer").classList.contains("d-none"), false);
  assert.equal(
      document.querySelector("#edit-offer").parentElement.classList.contains("action-group-end"),
      true);
  assert.equal(document.querySelector("#find-candidates").parentElement.classList.contains(
      "action-group-end"), true);
  assert.equal(document.querySelector("#upload-resume").parentElement.classList.contains(
      "action-group-start"), true);
  assert.equal(
      document.querySelector("#new-offer").parentElement.classList.contains("action-group-start"),
      true);
  assert.equal(
      document.querySelector(".action-panel").contains(document.querySelector("#upload-resume")),
      true);
  dom.window.close();
});

test("searches the selected job offer and displays matching candidates safely", async () => {
  const requests = [];
  const matchingCandidates = [
    {candidateName: "<img src=x onerror=alert(1)>", candidateEmail: "ada@example.com"},
  ];
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([offer]);
    }
    requests.push({url, options});
    return response(matchingCandidates);
  });
  const {document} = dom.window;
  const button = document.querySelector("#find-candidates");

  assert.equal(button.disabled, true);
  button.click();
  assert.equal(requests.length, 0);
  document.querySelector(".offer-button").click();
  assert.equal(button.disabled, false);
  button.click();
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(requests[0].url, "/api/candidate-matches");
  assert.equal(requests[0].options.method, "POST");
  assert.deepEqual(JSON.parse(requests[0].options.body), {jobOfferId: offer.id});
  assert.equal(document.querySelector("#offer-details").classList.contains("d-none"), true);
  assert.equal(document.querySelector("#search-results-panel").classList.contains("d-none"), false);
  assert.equal(document.querySelector("#search-results-heading").textContent, "Search Results");
  assert.equal(document.querySelector("#search-results-status").textContent,
      "Found 1 matching candidate.");
  assert.equal(document.querySelector("#search-results-list img"), null);
  assert.equal(document.querySelector("#search-results-list").textContent.includes("<img"), true);
  assert.equal(button.disabled, false);
  dom.window.close();
});

test("shows a searching state while the candidate request is pending", async () => {
  let completeSearch;
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([offer]);
    }
    return new Promise((resolve) => {
      completeSearch = resolve;
    });
  });
  const {document} = dom.window;
  document.querySelector(".offer-button").click();
  const button = document.querySelector("#find-candidates");
  button.click();

  assert.equal(button.disabled, true);
  assert.equal(document.querySelector("#search-results-status").textContent,
      "Searching the indexed resumes…");
  completeSearch(response([]));
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(button.disabled, false);
  dom.window.close();
});

test("communicates when the vector search returns no candidates", async () => {
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([offer]);
    }
    return response([]);
  });
  const {document} = dom.window;
  document.querySelector(".offer-button").click();
  document.querySelector("#find-candidates").click();
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(document.querySelector("#search-results-panel").classList.contains("d-none"), false);
  assert.equal(
      document.querySelector("#search-results-status").textContent,
      "No matching candidates were found for the current criteria. Review or loosen the job qualifications and try again.",
  );
  assert.equal(document.querySelector("#search-results-list").children.length, 0);
  dom.window.close();
});

test("shows a visible search error instead of successful results when the API fails", async () => {
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([offer]);
    }
    return response({code: "CANDIDATE_MATCHING_FAILED"}, false);
  });
  const {document} = dom.window;
  document.querySelector(".offer-button").click();
  document.querySelector("#find-candidates").click();
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(document.querySelector("#search-results-panel").classList.contains("d-none"), false);
  assert.equal(document.querySelector("#search-results-status").classList.contains("error"), true);
  assert.equal(document.querySelector("#search-results-status").textContent,
      "Candidate matching could not be completed. Please try again.");
  assert.equal(document.querySelector("#search-results-list").children.length, 0);
  dom.window.close();
});

test("keeps layout dimensions responsive and contains actions in their panel", async () => {
  for (const viewportWidth of [1600, 375]) {
    const dom = await loadPage(async () => response([offer]));
    const {document} = dom.window;
    dom.window.innerWidth = viewportWidth;

    assert.match(styles, /\.page-width\s*\{[^}]*width:\s*min\(1480px,\s*100%\)/s);
    assert.match(styles, /\.details-panel\s*\{[^}]*width:\s*100%;[^}]*max-width:\s*1024px/s);
    assert.match(styles, /\.action-panel\s*\{[^}]*max-width:\s*1480px;[^}]*width:\s*100%/s);
    assert.match(styles,
        /\.action-panel\s*\{[^}]*align-items:\s*center;[^}]*flex-wrap:\s*nowrap;[^}]*min-height:\s*56px;[^}]*overflow-x:\s*auto/s);
    assert.match(styles,
        /\.action-group\s*\{[^}]*align-self:\s*center;[^}]*align-items:\s*center;[^}]*display:\s*flex;[^}]*flex-wrap:\s*nowrap/s);
    assert.match(styles,
        /\.action-panel\s+\.btn\s*\{[^}]*align-self:\s*center;[^}]*align-items:\s*center;[^}]*flex:\s*0\s+0\s+auto;[^}]*height:\s*38px/s);
    assert.doesNotMatch(styles, /#upload-resume\s*\{[^}]*margin-top/s);
    assert.match(styles,
        /@media\s*\(max-width:\s*1320px\)\s*\{[^}]*\.workspace\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/s);

    const editButton = document.querySelector("#edit-offer");
    const newButton = document.querySelector("#new-offer");
    assert.equal(editButton.parentElement.classList.contains("action-group-end"), true);
    assert.equal(newButton.parentElement.classList.contains("action-group-start"), true);
    assert.equal(editButton.classList.contains("d-none"), true);
    assert.equal(newButton.classList.contains("d-none"), false);

    document.querySelector(".offer-button").click();
    assert.equal(editButton.classList.contains("d-none"), false);
    assert.equal(newButton.classList.contains("d-none"), false);
    dom.window.close();
  }
});

test("uses the editorial color, type, background, and motion guidelines accessibly", async () => {
  const dom = await loadPage(async () => response([]));
  const {document} = dom.window;

  assert.equal(document.querySelector('meta[name="theme-color"]').content, "#293d1c");
  assert.match(html, /family=IBM\+Plex\+Mono/);
  assert.match(html, /family=Space\+Grotesk/);
  assert.match(styles, /--forest:\s*#78a82f/i);
  assert.match(styles, /--copper:\s*#69aeca/i);
  assert.match(styles, /--ochre:\s*#ebcc64/i);
  assert.match(styles, /--paper:\s*#eaf7fb/i);
  assert.match(styles, /--display-font:\s*"Space Grotesk"/);
  assert.match(styles, /--mono-font:\s*"IBM Plex Mono"/);
  assert.doesNotMatch(styles, /font-family:\s*Inter\b/i);
  assert.match(styles, /radial-gradient\([\s\S]*repeating-linear-gradient/);
  assert.match(styles, /@keyframes\s+field-note-enter/);
  assert.match(styles, /@media\s*\(prefers-reduced-motion:\s*reduce\)/);
  assert.match(styles, /h1\s*\{[^}]*font-size:\s*clamp\(1\.5rem,\s*3\.36vw,\s*3rem\)/s);
  assert.match(styles, /\.subtitle\s*\{[^}]*white-space:\s*nowrap/s);
  assert.match(styles, /\.action-panel\s+\.btn\s*\{[^}]*height:\s*38px/s);
  assert.equal(document.querySelector("h1").textContent, "Find the signal in every application.");
  dom.window.close();
});

test("arranges requested detail and modal fields vertically in the specified order", async () => {
  const dom = await loadPage(async () => response([offer]));
  const {document} = dom.window;
  const detailLabels = [...document.querySelectorAll("#offer-details .detail-item h3")]
  .map((heading) => heading.textContent);
  const formLabels = [...document.querySelectorAll("#offer-form label")]
  .map((label) => label.textContent.trim());
  const requestedOrder = ["Description", "Responsibilities", "Required qualifications",
    "Nice to Have", "Benefits"];
  const requestedFormOrder = ["Job description", "Responsibilities", "Required qualifications",
    "Nice to Have", "Benefits"];

  assert.deepEqual(detailLabels.slice(0, 5), requestedOrder);
  assert.deepEqual(formLabels.filter((label) => requestedFormOrder.includes(label)),
      requestedFormOrder);
  assert.match(styles, /\.detail-grid\s*\{[^}]*grid-template-columns:\s*1fr/s);
  assert.match(styles, /\.details-panel\s*\{[^}]*width:\s*100%;[^}]*max-width:\s*1024px/s);
  dom.window.close();
});

test("renders required detail fields as Markdown and sanitizes unsafe markup and links",
    async () => {
      const markdownOffer = {
        ...offer,
        jobDescription: "# Platform role\n\n**Build** *reliable* services\n\n- First\n- Second\n\n[Safe](https://example.com) [Email](mailto:team@example.com)",
        requiredQualifications: "1. Java\n2. Spring\n\n[Unsafe](javascript:alert(1)) <img src=x onerror=alert(1)><script>alert(1)</script>",
      };
      const dom = await loadPage(async () => response([markdownOffer]));
      const {document} = dom.window;
      document.querySelector(".offer-button").click();

      assert.ok(document.querySelector("#detail-description h1"));
      assert.ok(document.querySelector("#detail-description strong"));
      assert.ok(document.querySelector("#detail-description em"));
      assert.equal(document.querySelectorAll("#detail-description ul li").length, 2);
      assert.equal(document.querySelectorAll("#detail-description a").length, 2);
      assert.equal(document.querySelector("#detail-description a").getAttribute("href"),
          "https://example.com");
      assert.equal(document.querySelectorAll("#detail-description a")[1].getAttribute("href"),
          "mailto:team@example.com");
      assert.equal(document.querySelectorAll("#detail-qualifications ol li").length, 2);
      assert.equal(document.querySelector("#detail-qualifications a")?.getAttribute("href"), null);
      assert.equal(document.querySelector("#detail-qualifications img").getAttribute("onerror"),
          null);
      assert.equal(document.querySelector("#detail-qualifications script"), null);
      assert.equal(document.querySelector("#detail-qualifications").textContent.includes("onerror"),
          false);
      dom.window.close();
    });

test("shows optional detail fields and renders legacy missing values as empty", async () => {
  const fullOffer = {
    ...offer,
    responsibilities: "**Own** platform reliability",
    benefits: "- Health insurance\n- Paid leave",
    niceToHave: "*Kubernetes* experience",
    compensation: "$120,000",
  };
  const legacyOffer = {
    id: "legacy",
    jobName: offer.jobName,
    jobDescription: offer.jobDescription,
    requiredQualifications: offer.requiredQualifications,
    location: offer.location,
    allocation: offer.allocation,
  };
  const dom = await loadPage(async () => response([fullOffer, legacyOffer]));
  const {document} = dom.window;
  document.querySelectorAll(".offer-button")[0].click();

  assert.ok(document.querySelector("#detail-responsibilities strong"));
  assert.equal(document.querySelectorAll("#detail-benefits li").length, 2);
  assert.ok(document.querySelector("#detail-nice-to-have em"));
  assert.equal(document.querySelector("#detail-compensation").textContent, "$120,000");

  document.querySelectorAll(".offer-button")[1].click();
  assert.equal(document.querySelector("#detail-responsibilities").textContent, "");
  assert.equal(document.querySelector("#detail-benefits").textContent, "");
  assert.equal(document.querySelector("#detail-nice-to-have").textContent, "");
  assert.equal(document.querySelector("#detail-compensation").textContent, "");
  dom.window.close();
});

test("opens a blank required form with the specified allocation options and no early errors",
    async () => {
      const dom = await loadPage(async () => response([]));
      const {document} = dom.window;
      document.querySelector("#new-offer").click();
      assert.equal(document.querySelector("#offer-modal").getAttribute("data-bs-backdrop"),
          "static");
      assert.equal(document.querySelector("#offer-modal").getAttribute("data-bs-keyboard"),
          "false");

      assert.equal(document.querySelector("#offer-modal").classList.contains("show"), true);
      assert.equal(document.querySelector("#job-name").value, "");
      assert.equal(document.querySelectorAll("#job-allocation option").length, 4);
      assert.equal(document.querySelectorAll(
              "#offer-form [name='responsibilities'], #offer-form [name='benefits'], #offer-form [name='companyName'], #offer-form [name='niceToHave'], #offer-form [name='compensation']").length,
          5);
      assert.equal(document.querySelector("#responsibilities").required, false);
      assert.equal(document.querySelector("#benefits").required, false);
      assert.equal(document.querySelector("#company-name").required, false);
      assert.equal(document.querySelector("#nice-to-have").required, false);
      assert.equal(document.querySelector("#compensation").required, false);
      assert.deepEqual(
          [...document.querySelectorAll("#job-allocation option")].slice(1).map(
              (option) => option.value),
          ["Remote", "Hybrid", "On-site"],
      );
      assert.equal(document.querySelectorAll(".is-invalid").length, 0);
      assert.ok(document.querySelector("#cancel-offer"));
      assert.ok(document.querySelector("#save-offer"));
      dom.window.close();
    });

test("opens and cancels the Resume Data modal without submitting a resume", async () => {
  const requests = [];
  const dom = await loadPage(async (url, options) => {
    requests.push({url, options});
    return response([]);
  });
  const {document} = dom.window;
  const uploadButton = document.querySelector("#upload-resume");
  const modal = document.querySelector("#resume-modal");

  assert.equal(uploadButton.parentElement.classList.contains("action-group-start"), true);
  uploadButton.click();
  assert.equal(modal.classList.contains("show"), true);
  assert.equal(document.querySelector("#resume-modal-title").textContent, "Resume Data");
  assert.equal(document.querySelector("#resume-candidate-name").required, true);
  assert.equal(document.querySelector("#resume-candidate-email").required, true);
  assert.equal(document.querySelector("#resume-file").required, true);
  assert.equal(document.querySelector("#cancel-resume").textContent, "Cancel");
  assert.equal(document.querySelector("#save-resume").textContent, "Save");
  document.querySelector("#cancel-resume").click();

  assert.equal(modal.classList.contains("show"), false);
  assert.equal(requests.filter(({url}) => url === "/api/resumes").length, 0);
  dom.window.close();
});

test("submits a resume as multipart data and closes after success", async () => {
  let uploadRequest;
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([]);
    }
    uploadRequest = {url, options};
    return response({candidateName: "Ada Lovelace"});
  });
  const {document, File} = dom.window;
  document.querySelector("#upload-resume").click();
  document.querySelector("#resume-candidate-name").value = "Ada Lovelace";
  document.querySelector("#resume-candidate-email").value = "ada@example.com";
  const file = new File(["%PDF-sample"], "resume.pdf", {type: "application/pdf"});
  Object.defineProperty(document.querySelector("#resume-file"), "files", {value: [file]});

  document.querySelector("#resume-form").dispatchEvent(
      new dom.window.Event("submit", {bubbles: true, cancelable: true}),
  );
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(uploadRequest.url, "/api/resumes");
  assert.equal(uploadRequest.options.method, "POST");
  assert.equal(uploadRequest.options.headers, undefined);
  assert.equal(uploadRequest.options.body.get("candidateName"), "Ada Lovelace");
  assert.equal(uploadRequest.options.body.get("candidateEmail"), "ada@example.com");
  assert.equal(uploadRequest.options.body.get("file").name, "resume.pdf");
  assert.equal(document.querySelector("#resume-modal").classList.contains("show"), false);
  dom.window.close();
});

test("keeps the resume modal open with a visible server error after failure", async () => {
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([]);
    }
    return response({message: "Resume could not be saved."}, false);
  });
  const {document, File} = dom.window;
  document.querySelector("#upload-resume").click();
  document.querySelector("#resume-candidate-name").value = "Ada Lovelace";
  document.querySelector("#resume-candidate-email").value = "ada@example.com";
  Object.defineProperty(document.querySelector("#resume-file"), "files", {
    value: [new File(["%PDF-sample"], "resume.pdf", {type: "application/pdf"})],
  });

  document.querySelector("#resume-form").dispatchEvent(
      new dom.window.Event("submit", {bubbles: true, cancelable: true}),
  );
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(document.querySelector("#resume-modal").classList.contains("show"), true);
  assert.equal(document.querySelector("#resume-upload-error").textContent,
      "Resume could not be saved.");
  assert.equal(document.querySelector("#resume-upload-error").classList.contains("d-none"), false);
  dom.window.close();
});

test("blocks incomplete resume form submissions", async () => {
  const requests = [];
  const dom = await loadPage(async (url, options) => {
    requests.push({url, options});
    return response([]);
  });
  const {document} = dom.window;
  document.querySelector("#upload-resume").click();
  document.querySelector("#resume-form").dispatchEvent(
      new dom.window.Event("submit", {bubbles: true, cancelable: true}),
  );
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(document.querySelectorAll("#resume-form .is-invalid").length, 3);
  assert.equal(requests.some(({url}) => url === "/api/resumes"), false);
  assert.equal(document.querySelector("#resume-modal").classList.contains("show"), true);
  dom.window.close();
});

test("cancel closes a pristine form and asks before discarding changed values", async () => {
  const dom = await loadPage(async () => response([]));
  const {document} = dom.window;
  document.querySelector("#new-offer").click();
  document.querySelector("#cancel-offer").click();
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), false);

  document.querySelector("#new-offer").click();
  document.querySelector("#job-name").value = "Draft";
  document.querySelector("#cancel-offer").click();
  assert.equal(document.querySelector("#discard-confirmation").classList.contains("d-none"), false);
  document.querySelector("#continue-editing").click();
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), true);
  assert.equal(document.querySelector("#job-name").value, "Draft");

  document.querySelector("#cancel-offer").click();
  document.querySelector("#discard-changes").click();
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), false);
  dom.window.close();
});

test("marks every empty field invalid only after save", async () => {
  const dom = await loadPage(async () => response([]));
  const {document} = dom.window;
  document.querySelector("#new-offer").click();
  assert.equal(document.querySelectorAll(".is-invalid").length, 0);
  await submitForm(dom);

  assert.equal(document.querySelectorAll(".is-invalid").length, 5);
  assert.equal(document.querySelectorAll('[aria-invalid="true"]').length, 5);
  assert.equal(document.querySelectorAll(".invalid-feedback.d-block").length, 5);
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), true);
  dom.window.close();
});

test("prefills the edit form and saves changes, selecting refreshed offer", async () => {
  let updateRequest;
  const updated = {
    ...offer,
    jobName: "Senior Backend Engineer",
    allocation: "Hybrid",
    companyName: "Updated Corp"
  };
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([offer]);
    }
    updateRequest = {url, options};
    return response(updated);
  });
  const {document} = dom.window;
  document.querySelector(".offer-button").click();
  document.querySelector("#edit-offer").click();
  assert.equal(document.querySelector("#job-name").value, offer.jobName);
  assert.equal(document.querySelector("#responsibilities").value, offer.responsibilities);
  assert.equal(document.querySelector("#benefits").value, offer.benefits);
  assert.equal(document.querySelector("#company-name").value, offer.companyName);
  assert.equal(document.querySelector("#nice-to-have").value, offer.niceToHave);
  assert.equal(document.querySelector("#compensation").value, offer.compensation);
  fillForm(document, updated);
  await submitForm(dom);

  assert.equal(updateRequest.url, "/api/job-offers/offer-1");
  assert.equal(updateRequest.options.method, "PUT");
  assert.equal(JSON.parse(updateRequest.options.body).allocation, "Hybrid");
  assert.deepEqual(
      Object.fromEntries(Object.entries(JSON.parse(updateRequest.options.body)).filter(([key]) =>
          ["responsibilities", "benefits", "companyName", "niceToHave", "compensation"].includes(
              key))),
      {
        responsibilities: updated.responsibilities,
        benefits: updated.benefits,
        companyName: updated.companyName,
        niceToHave: updated.niceToHave,
        compensation: updated.compensation,
      },
  );
  assert.equal(document.querySelector("#detail-name").textContent, updated.jobName);
  assert.equal(document.querySelector("#detail-allocation").textContent, "Hybrid");
  assert.equal(document.querySelector(".offer-company").textContent, "Updated Corp");
  assert.equal(document.querySelector(".offer-button").getAttribute("aria-current"), "true");
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), false);
  dom.window.close();
});

test("creates offer and shows selected details after successful save", async () => {
  let createRequest;
  const created = {...offer, id: "new-offer"};
  const dom = await loadPage(async (url, options) => {
    if (!options) {
      return response([]);
    }
    createRequest = {url, options};
    return response(created);
  });
  const {document} = dom.window;
  document.querySelector("#new-offer").click();
  fillForm(document);
  await submitForm(dom);

  assert.equal(createRequest.url, "/api/job-offers");
  assert.equal(createRequest.options.method, "POST");
  assert.deepEqual(
      Object.fromEntries(Object.entries(JSON.parse(createRequest.options.body)).filter(([key]) =>
          ["responsibilities", "benefits", "companyName", "niceToHave", "compensation"].includes(
              key))),
      {
        responsibilities: offer.responsibilities,
        benefits: offer.benefits,
        companyName: offer.companyName,
        niceToHave: offer.niceToHave,
        compensation: offer.compensation,
      },
  );
  assert.equal(document.querySelectorAll(".offer-button").length, 1);
  assert.equal(document.querySelector(".offer-button").getAttribute("aria-current"), "true");
  assert.equal(document.querySelector("#detail-name").textContent, offer.jobName);
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), false);
  dom.window.close();
});

test("keeps the modal open and displays a save error when persistence fails", async () => {
  const dom = await loadPage(async (url, options) => options ? response({}, false) : response([]));
  const {document} = dom.window;
  document.querySelector("#new-offer").click();
  fillForm(document);
  await submitForm(dom);

  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), true);
  assert.equal(document.querySelector("#save-error").classList.contains("d-none"), false);
  assert.match(document.querySelector("#save-error").textContent, /could not be saved/);
  dom.window.close();
});

test("shows empty and failed-load states distinctly", async () => {
  const emptyDom = await loadPage(async () => response([]));
  assert.match(emptyDom.window.document.querySelector("#list-status").textContent, /No job offers/);
  emptyDom.window.close();

  const failedDom = await loadPage(async () => {
    throw new Error("offline");
  });
  assert.match(failedDom.window.document.querySelector("#list-status").textContent,
      /could not be loaded/);
  failedDom.window.close();
});
