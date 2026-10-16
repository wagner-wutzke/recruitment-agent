const offerList = document.querySelector("#offer-list");
const listStatus = document.querySelector("#list-status");
const offerCount = document.querySelector("#offer-count");
const detailsEmpty = document.querySelector("#details-empty");
const offerDetails = document.querySelector("#offer-details");
const editButton = document.querySelector("#edit-offer");
const modalElement = document.querySelector("#offer-modal");
const form = document.querySelector("#offer-form");
const fields = ["jobName", "jobDescription", "requiredQualifications", "location", "allocation"];
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

function setText(selector, value) {
  document.querySelector(selector).textContent = value ?? "";
}

function formValues() {
  return Object.fromEntries(fields.map((name) => [name, fieldElements[name].value]));
}

function isDirty() {
  return JSON.stringify(formValues()) !== JSON.stringify(originalValues);
}

function selectOffer(offer) {
  selectedOfferId = offer.id;
  offerList.querySelectorAll("button").forEach(
      (item) => item.setAttribute("aria-current", "false"));
  const selectedButton = [...offerList.querySelectorAll("button")].find(
      (button) => button.dataset.offerId === offer.id);
  selectedButton?.setAttribute("aria-current", "true");
  setText("#detail-name", offer.jobName);
  setText("#detail-description", offer.jobDescription);
  setText("#detail-qualifications", offer.requiredQualifications);
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
    button.append(name, location);
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
    offerDetails.classList.add("d-none");
    detailsEmpty.classList.remove("d-none");
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
  fields.forEach((name) => {
    const element = fieldElements[name];
    element.classList.remove("is-invalid");
    element.removeAttribute("aria-invalid");
    document.querySelector(fieldErrors[name]).classList.remove("d-block");
  });
}

function validateForm() {
  let valid = true;
  fields.forEach((name) => {
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
