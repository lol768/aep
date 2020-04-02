const INTERVAL_MS = 30 * 1000;

const el = document.querySelectorAll('.studentAssessmentInfo')[0];
const refreshTable = () => setTimeout(() => {
  fetch(`/ajax/reporting/${el.getAttribute('data-id')}/${el.getAttribute('data-route')}`)
    .then((response) => {
      el.innerHtml = response.text();
    });
  refreshTable();
}, INTERVAL_MS);

refreshTable();
