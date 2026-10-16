const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const { JSDOM } = require("jsdom");

const root = path.resolve(__dirname, "../../..");
const html = fs.readFileSync(path.join(root, "src/main/resources/static/index.html"), "utf8");
const script = fs.readFileSync(path.join(root, "src/main/resources/static/app.js"), "utf8");
const offer = {
  id: "offer-1",
  jobName: "Backend Engineer",
  jobDescription: "Build backend services",
  requiredQualifications: "Five years of Java experience",
  location: "Dublin",
  allocation: "Remote",
};

async function loadPage(fetchImplementation) {
  const dom = new JSDOM(html, { runScripts: "outside-only", url: "http://localhost/" });
  dom.window.fetch = fetchImplementation;
  dom.window.eval(script);
  await new Promise((resolve) => setImmediate(resolve));
  return dom;
}

function response(body, ok = true) {
  return { ok, json: async () => body };
}

function fillForm(document, values = offer) {
  document.querySelector("#job-name").value = values.jobName;
  document.querySelector("#job-description").value = values.jobDescription;
  document.querySelector("#job-qualifications").value = values.requiredQualifications;
  document.querySelector("#job-location").value = values.location;
  document.querySelector("#job-allocation").value = values.allocation;
}

async function submitForm(dom) {
  dom.window.document.querySelector("#offer-form").dispatchEvent(
    new dom.window.Event("submit", { bubbles: true, cancelable: true }),
  );
  await new Promise((resolve) => setImmediate(resolve));
}

test("loads offers, selects details, and shows management controls appropriately", async () => {
  const dom = await loadPage(async () => response([offer]));
  const { document } = dom.window;

  assert.equal(document.querySelectorAll(".offer-button").length, 1);
  assert.equal(document.querySelector("#edit-offer").classList.contains("d-none"), true);
  assert.equal(document.querySelector("#new-offer").textContent, "New Job Offer");
  document.querySelector(".offer-button").click();
  assert.equal(document.querySelector("#detail-name").textContent, offer.jobName);
  assert.equal(document.querySelector("#edit-offer").classList.contains("d-none"), false);
  dom.window.close();
});

test("opens a blank required form with the specified allocation options and no early errors", async () => {
  const dom = await loadPage(async () => response([]));
  const { document } = dom.window;
  document.querySelector("#new-offer").click();
  assert.equal(document.querySelector("#offer-modal").getAttribute("data-bs-backdrop"), "static");
  assert.equal(document.querySelector("#offer-modal").getAttribute("data-bs-keyboard"), "false");

  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), true);
  assert.equal(document.querySelector("#job-name").value, "");
  assert.equal(document.querySelectorAll("#job-allocation option").length, 4);
  assert.deepEqual(
    [...document.querySelectorAll("#job-allocation option")].slice(1).map((option) => option.value),
    ["Remote", "Hybrid", "On-site"],
  );
  assert.equal(document.querySelectorAll(".is-invalid").length, 0);
  assert.ok(document.querySelector("#cancel-offer"));
  assert.ok(document.querySelector("#save-offer"));
  dom.window.close();
});

test("cancel closes a pristine form and asks before discarding changed values", async () => {
  const dom = await loadPage(async () => response([]));
  const { document } = dom.window;
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
  const { document } = dom.window;
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
  const updated = { ...offer, jobName: "Senior Backend Engineer", allocation: "Hybrid" };
  const dom = await loadPage(async (url, options) => {
    if (!options) return response([offer]);
    updateRequest = { url, options };
    return response(updated);
  });
  const { document } = dom.window;
  document.querySelector(".offer-button").click();
  document.querySelector("#edit-offer").click();
  assert.equal(document.querySelector("#job-name").value, offer.jobName);
  fillForm(document, updated);
  await submitForm(dom);

  assert.equal(updateRequest.url, "/api/job-offers/offer-1");
  assert.equal(updateRequest.options.method, "PUT");
  assert.equal(JSON.parse(updateRequest.options.body).allocation, "Hybrid");
  assert.equal(document.querySelector("#detail-name").textContent, updated.jobName);
  assert.equal(document.querySelector("#detail-allocation").textContent, "Hybrid");
  assert.equal(document.querySelector(".offer-button").getAttribute("aria-current"), "true");
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), false);
  dom.window.close();
});

test("creates offer and shows selected details after successful save", async () => {
  let createRequest;
  const created = { ...offer, id: "new-offer" };
  const dom = await loadPage(async (url, options) => {
    if (!options) return response([]);
    createRequest = { url, options };
    return response(created);
  });
  const { document } = dom.window;
  document.querySelector("#new-offer").click();
  fillForm(document);
  await submitForm(dom);

  assert.equal(createRequest.url, "/api/job-offers");
  assert.equal(createRequest.options.method, "POST");
  assert.equal(document.querySelectorAll(".offer-button").length, 1);
  assert.equal(document.querySelector(".offer-button").getAttribute("aria-current"), "true");
  assert.equal(document.querySelector("#detail-name").textContent, offer.jobName);
  assert.equal(document.querySelector("#offer-modal").classList.contains("show"), false);
  dom.window.close();
});

test("keeps the modal open and displays a save error when persistence fails", async () => {
  const dom = await loadPage(async (url, options) => options ? response({}, false) : response([]));
  const { document } = dom.window;
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

  const failedDom = await loadPage(async () => { throw new Error("offline"); });
  assert.match(failedDom.window.document.querySelector("#list-status").textContent, /could not be loaded/);
  failedDom.window.close();
});
