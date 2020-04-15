import Tablesort from 'tablesort';

const INTERVAL_MS = 30 * 1000;

const el = document.querySelectorAll('.studentAssessmentInfo')[0];
const refreshTable = () => setTimeout(() => {
  const selectedSortHeader = document.querySelector('.students-taking-assessment-table th[aria-sort]');
  const selectedSortHeaderValue = selectedSortHeader !== null ? selectedSortHeader.innerText : '';
  const sortDirection = selectedSortHeader !== null ? selectedSortHeader.getAttribute('aria-sort') : '';
  fetch(`/ajax/reporting/${el.getAttribute('data-id')}/${el.getAttribute('data-route')}?sort=${selectedSortHeaderValue}&direction=${sortDirection}`)
    .then((response) => response.text())
    .then((responseText) => {
      el.innerHTML = responseText;
      const sort = Tablesort(document.querySelector('.students-taking-assessment-table'), {
        descending: sortDirection === 'descending',
      });
      sort.refresh();
    });
  refreshTable();
}, INTERVAL_MS);

refreshTable();
