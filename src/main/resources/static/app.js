const offerList = document.querySelector("#offer-list");
const listStatus = document.querySelector("#list-status");
const offerCount = document.querySelector("#offer-count");
const detailsEmpty = document.querySelector("#details-empty");
const offerDetails = document.querySelector("#offer-details");
const editButton = document.querySelector("#edit-offer");
const findCandidatesButton = document.querySelector("#find-candidates");
const searchResultsPanel = document.querySelector("#search-results-panel");
const searchResultsStatus = document.querySelector("#search-results-status");
const searchResultsList = document.querySelector("#search-results-list");
const modalElement = document.querySelector("#offer-modal");
const resumeModalElement = document.querySelector("#resume-modal");
const resumeForm = document.querySelector("#resume-form");
const form = document.querySelector("#offer-form");
const fields = [
  "jobName",
  "jobDescription",
  "requiredQualifications",
  "location",
  "allocation",
  "responsibilities",
  "benefits",
  "companyName",
  "niceToHave",
  "compensation",
];
const requiredFields = ["jobName", "jobDescription", "requiredQualifications", "location",
  "allocation"];
const fieldElements = Object.fromEntries(
    fields.map((name) => [name, form.elements.namedItem(name)]));
const fieldErrors = {
  jobName: "#job-name-error",
  jobDescription: "#job-description-error",
  requiredQualifications: "#job-qualifications-error",
  location: "#job-location-error",
  allocation: "#job-allocation-error",
};

let offers = [];
let selectedOfferId = null;
let editingOfferId = null;
let originalValues = null;
let saveAttempted = false;
let modalInstance;
let resumeModalInstance;

function setText(selector, value) {
  document.querySelector(selector).textContent = value ?? "";
}

function setMarkdown(selector, value) {
  const element = document.querySelector(selector);
  if (value == null || value === "") {
    element.replaceChildren();
    return;
  }
  if (!window.marked || !window.DOMPurify) {
    element.textContent = value;
    return;
  }
  const html = window.marked.parse(value);
  element.innerHTML = window.DOMPurify.sanitize(html, {
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|(?:[/.#][^:]*)$)/i,
  });
}

function formValues() {
  return Object.fromEntries(fields.map((name) => [name, fieldElements[name].value]));
}

function isDirty() {
  return JSON.stringify(formValues()) !== JSON.stringify(originalValues);
}

function selectOffer(offer) {
  selectedOfferId = offer.id;
  findCandidatesButton.disabled = false;
  searchResultsPanel.classList.add("d-none");
  searchResultsList.replaceChildren();
  searchResultsStatus.textContent = "";
  searchResultsStatus.classList.remove("error");
  offerList.querySelectorAll("button").forEach(
      (item) => item.setAttribute("aria-current", "false"));
  const selectedButton = [...offerList.querySelectorAll("button")].find(
      (button) => button.dataset.offerId === offer.id);
  selectedButton?.setAttribute("aria-current", "true");
  setText("#detail-name", offer.jobName);
  setMarkdown("#detail-description", offer.jobDescription);
  setMarkdown("#detail-qualifications", offer.requiredQualifications);
  setMarkdown("#detail-responsibilities", offer.responsibilities);
  setMarkdown("#detail-benefits", offer.benefits);
  setMarkdown("#detail-nice-to-have", offer.niceToHave);
  setText("#detail-compensation", offer.compensation);
  setText("#detail-location", offer.location);
  setText("#detail-allocation", offer.allocation);
  detailsEmpty.classList.add("d-none");
  offerDetails.classList.remove("d-none");
  editButton.classList.remove("d-none");
}

function renderOffers() {
  offerList.replaceChildren();
  offerCount.textContent = String(offers.length);
  listStatus.textContent = offers.length === 0 ? "No job offers are available yet." : "";
  listStatus.classList.remove("error");
  offers.forEach((offer) => {
    const item = document.createElement("li");
    const button = document.createElement("button");
    button.className = "offer-button";
    button.type = "button";
    button.dataset.offerId = offer.id;
    button.setAttribute("aria-current", "false");
    const name = document.createElement("strong");
    name.textContent = offer.jobName ?? "Untitled role";
    const location = document.createElement("span");
    location.textContent = [offer.location, offer.allocation].filter(Boolean).join(" · ");
    const company = document.createElement("span");
    company.className = "offer-company";
    company.textContent = offer.companyName ?? "";
    button.append(name, location, company);
    button.addEventListener("click", () => selectOffer(offer));
    item.append(button);
    offerList.append(item);
  });
  const selectedOffer = offers.find((offer) => offer.id === selectedOfferId);
  if (selectedOffer) {
    selectOffer(selectedOffer);
  } else {
    selectedOfferId = null;
    editButton.classList.add("d-none");
    findCandidatesButton.disabled = true;
    offerDetails.classList.add("d-none");
    searchResultsPanel.classList.add("d-none");
    detailsEmpty.classList.remove("d-none");
  }
}

function displaySearchResults(candidates) {
  searchResultsList.replaceChildren();
  if (candidates.length === 0) {
    searchResultsStatus.textContent =
        "No matching candidates were found for the current criteria. Review or loosen the job qualifications and try again.";
    return;
  }

  searchResultsStatus.textContent = `Found ${candidates.length} matching candidate${candidates.length
  === 1 ? "" : "s"}.`;
  candidates.forEach((candidate) => {
    const item = document.createElement("li");
    item.className = "candidate-match";
    const name = document.createElement("strong");
    name.textContent = candidate.candidateName ?? "Unnamed candidate";
    const email = document.createElement("span");
    email.textContent = candidate.candidateEmail ?? "";
    item.append(name, email);
    searchResultsList.append(item);
  });
}

async function findCandidates() {
  if (!selectedOfferId) {
    return;
  }

  const searchedOfferId = selectedOfferId;
  findCandidatesButton.disabled = true;
  detailsEmpty.classList.add("d-none");
  offerDetails.classList.add("d-none");
  searchResultsPanel.classList.remove("d-none");
  searchResultsList.replaceChildren();
  searchResultsStatus.textContent = "Searching the indexed resumes…";
  searchResultsStatus.classList.remove("error");

  try {
    const response = await fetch("/api/candidate-matches", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({jobOfferId: searchedOfferId}),
    });
    if (!response.ok) {
      throw new Error("Candidate matching could not be completed. Please try again.");
    }
    const candidates = await response.json();
    if (!Array.isArray(candidates)) {
      throw new Error("Candidate matching could not be completed. Please try again.");
    }
    if (selectedOfferId !== searchedOfferId) {
      return;
    }
    displaySearchResults(candidates);
  } catch {
    if (selectedOfferId !== searchedOfferId) {
      return;
    }
    searchResultsList.replaceChildren();
    searchResultsStatus.textContent = "Candidate matching could not be completed. Please try again.";
    searchResultsStatus.classList.add("error");
  } finally {
    if (selectedOfferId === searchedOfferId) {
      findCandidatesButton.disabled = false;
    }
  }
}

async function loadOffers() {
  try {
    const response = await fetch("/api/job-offers");
    if (!response.ok) {
      throw new Error("Job offers request failed");
    }
    const result = await response.json();
    if (!Array.isArray(result)) {
      throw new Error("Invalid job offers response");
    }
    offers = result;
    renderOffers();
  } catch {
    offerList.replaceChildren();
    offerCount.textContent = "!";
    listStatus.textContent = "Job offers could not be loaded. Please try again later.";
    listStatus.classList.add("error");
  }
}

function resetValidation() {
  saveAttempted = false;
  requiredFields.forEach((name) => {
    const element = fieldElements[name];
    element.classList.remove("is-invalid");
    element.removeAttribute("aria-invalid");
    document.querySelector(fieldErrors[name]).classList.remove("d-block");
  });
}

function validateForm() {
  let valid = true;
  requiredFields.forEach((name) => {
    const element = fieldElements[name];
    const fieldIsValid = element.value.trim() !== "";
    element.classList.toggle("is-invalid", !fieldIsValid);
    if (fieldIsValid) {
      element.removeAttribute("aria-invalid");
    } else {
      element.setAttribute("aria-invalid", "true");
    }
    document.querySelector(fieldErrors[name]).classList.toggle("d-block", !fieldIsValid);
    valid = valid && fieldIsValid;
  });
  return valid;
}

function openForm(offer = null) {
  editingOfferId = offer?.id ?? null;
  form.reset();
  fields.forEach((name) => {
    fieldElements[name].value = offer?.[name] ?? "";
  });
  originalValues = formValues();
  resetValidation();
  setText("#offer-modal-title", offer ? "Edit Job Offer" : "New Job Offer");
  document.querySelector("#save-error").classList.add("d-none");
  document.querySelector("#save-error").textContent = "";
  modalInstance ??= window.bootstrap?.Modal.getOrCreateInstance(modalElement) ?? fallbackModal();
  modalInstance.show();
}

function fallbackModal() {
  return {
    show() {
      modalElement.classList.add("show");
      modalElement.style.display = "block";
      modalElement.removeAttribute("aria-hidden");
    },
    hide() {
      modalElement.classList.remove("show");
      modalElement.style.display = "none";
      modalElement.setAttribute("aria-hidden", "true");
    },
  };
}

function fallbackResumeModal() {
  return {
    show() {
      resumeModalElement.classList.add("show");
      resumeModalElement.style.display = "block";
      resumeModalElement.removeAttribute("aria-hidden");
    },
    hide() {
      resumeModalElement.classList.remove("show");
      resumeModalElement.style.display = "none";
      resumeModalElement.setAttribute("aria-hidden", "true");
    },
  };
}

function openResumeForm() {
  resumeForm.reset();
  resumeForm.querySelectorAll(".is-invalid").forEach((field) => {
    field.classList.remove("is-invalid");
    field.removeAttribute("aria-invalid");
  });
  document.querySelector("#resume-upload-error").classList.add("d-none");
  resumeModalInstance ??= window.bootstrap?.Modal.getOrCreateInstance(resumeModalElement)
      ?? fallbackResumeModal();
  resumeModalInstance.show();
}

function requestClose() {
  if (!isDirty()) {
    modalInstance.hide();
    return;
  }
  document.querySelector("#discard-confirmation").classList.remove("d-none");
  document.querySelector("#discard-changes").focus();
}

function hideDiscardDialog() {
  document.querySelector("#discard-confirmation").classList.add("d-none");
}

document.querySelector("#new-offer").addEventListener("click", () => openForm());
findCandidatesButton.addEventListener("click", findCandidates);
document.querySelector("#upload-resume").addEventListener("click", openResumeForm);
document.querySelector("#cancel-resume").addEventListener("click",
    () => resumeModalInstance?.hide());
document.querySelector("#resume-modal-close").addEventListener("click",
    () => resumeModalInstance?.hide());
resumeForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const required = [
    resumeForm.elements.namedItem("candidateName"),
    resumeForm.elements.namedItem("candidateEmail"),
    resumeForm.elements.namedItem("file"),
  ];
  const valid = required.map((field) => {
    const fieldIsValid = field.type === "file" ? field.files.length > 0 : field.value.trim() !== "";
    field.classList.toggle("is-invalid", !fieldIsValid);
    if (fieldIsValid) {
      field.removeAttribute("aria-invalid");
    } else {
      field.setAttribute("aria-invalid", "true");
    }
    return fieldIsValid;
  }).every(Boolean);
  if (!valid) {
    return;
  }

  const errorMessage = document.querySelector("#resume-upload-error");
  errorMessage.classList.add("d-none");
  try {
    const formData = new FormData();
    formData.append("candidateName", required[0].value.trim());
    formData.append("candidateEmail", required[1].value.trim());
    formData.append("file", required[2].files[0]);
    const response = await fetch("/api/resumes", {
      method: "POST",
      body: formData,
    });
    if (!response.ok) {
      const failure = await response.json().catch(() => ({}));
      throw new Error(failure.message || "Resume could not be saved. Please try again.");
    }
    resumeModalInstance.hide();
    resumeForm.reset();
  } catch (error) {
    errorMessage.textContent = error.message || "Resume could not be saved. Please try again.";
    errorMessage.classList.remove("d-none");
  }
});
editButton.addEventListener("click", () => {
  const offer = offers.find((item) => item.id === selectedOfferId);
  if (offer) {
    openForm(offer);
  }
});
document.querySelector("#cancel-offer").addEventListener("click", requestClose);
document.querySelector("#modal-close").addEventListener("click", requestClose);
document.querySelector("#discard-changes").addEventListener("click", () => {
  hideDiscardDialog();
  modalInstance.hide();
});
document.querySelector("#continue-editing").addEventListener("click", hideDiscardDialog);

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  saveAttempted = true;
  if (!validateForm()) {
    return;
  }
  const errorMessage = document.querySelector("#save-error");
  errorMessage.classList.add("d-none");
  const url = editingOfferId ? `/api/job-offers/${encodeURIComponent(editingOfferId)}`
      : "/api/job-offers";
  const method = editingOfferId ? "PUT" : "POST";
  try {
    const response = await fetch(url, {
      method,
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(formValues()),
    });
    if (!response.ok) {
      throw new Error("Save failed");
    }
    const savedOffer = await response.json();
    const index = offers.findIndex((offer) => offer.id === savedOffer.id);
    if (index < 0) {
      offers.push(savedOffer);
    } else {
      offers[index] = savedOffer;
    }
    selectedOfferId = savedOffer.id;
    renderOffers();
    modalInstance.hide();
  } catch {
    errorMessage.textContent = "The job offer could not be saved. Please try again.";
    errorMessage.classList.remove("d-none");
  }
});

fields.forEach((name) => {
  const updateValidation = () => {
    if (saveAttempted) {
      validateForm();
    }
  };
  fieldElements[name].addEventListener("input", updateValidation);
  fieldElements[name].addEventListener("change", updateValidation);
});

loadOffers();
