function registerListeners() {
  window.addEventListener('beforeunload', (event) => {
    // Cancel the event as stated by the standard.
    event.preventDefault();
    // Chrome requires returnValue to be set.
    /* eslint no-param-reassign:0 */
    event.returnValue = '';
    return true;
  });
}

registerListeners();
