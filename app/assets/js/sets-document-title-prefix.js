export const originalDocumentTitle = document.title;
export function updateDocumentTitle(el) {
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
