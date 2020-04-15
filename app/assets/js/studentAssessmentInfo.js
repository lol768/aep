const INTERVAL_MS = 30 * 1000;

const originalDocumentTitle = document.title;
function updateDocumentTitle(el) {
  let prefix = '';
  const titlePrefixElement = el.getElementsByClassName('sets-document-title-prefix')[0];
  if (titlePrefixElement) {
    prefix = titlePrefixElement.getAttribute('data-document-title-prefix');
  }
  if (prefix.trim().length !== 0) {
    document.title = `${prefix.trim()} ${originalDocumentTitle}`;
  } else {
    document.title = originalDocumentTitle;
  }
}

const el = document.querySelectorAll('.studentAssessmentInfo')[0];
const refreshTable = () => setTimeout(() => {
  fetch(`/ajax/reporting/${el.getAttribute('data-id')}/${el.getAttribute('data-route')}`)
    .then((response) => response.text())
    .then((responseText) => {
      el.innerHTML = responseText;
      updateDocumentTitle(el);
    });
  refreshTable();
}, INTERVAL_MS);

refreshTable();
updateDocumentTitle(document);
