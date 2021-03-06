import Tablesort from 'tablesort';
import * as log from './log';
import TablesortNumber from './tablesort.number';
import { updateDocumentTitle } from './sets-document-title-prefix';

TablesortNumber();

const INTERVAL_MS = 30 * 1000;

const el = document.querySelectorAll('.studentAssessmentInfo')[0];
const refreshTable = () => setTimeout(() => {
  const selectedSortHeader = document.querySelector('.students-taking-assessment-table th[aria-sort]');
  const selectedSortHeaderValue = selectedSortHeader !== null ? selectedSortHeader.innerText : '';
  const sortDirection = selectedSortHeader !== null ? selectedSortHeader.getAttribute('aria-sort') : '';
  fetch(`/ajax/reporting/${el.getAttribute('data-id')}/${el.getAttribute('data-route')}?sort=${selectedSortHeaderValue}&direction=${sortDirection}`)
    .then((response) => (
      response.status === 200 ? response.text() : Promise.reject(response.statusText)
    ))
    .then((responseText) => {
      el.innerHTML = responseText;
      updateDocumentTitle(el);
      const sort = Tablesort(document.querySelector('.students-taking-assessment-table'), {
        descending: sortDirection === 'descending',
      });
      sort.refresh();
      refreshTable();
    }).catch((e) => {
      log.info('Failed to update student assessment information from server. Re-scheduling', e);
      el.querySelector('.auto-update').classList.add('hidden');
      el.querySelector('.auto-update-error').classList.remove('hidden');
      refreshTable();
    });
}, INTERVAL_MS);

refreshTable();
updateDocumentTitle(document);
