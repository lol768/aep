function registerListeners() {
  window.addEventListener('beforeunload', (event) => {
    if (document.body.classList.contains('quash-beforeunload')) {
      return;
    }
    // Cancel the event as stated by the standard.
    event.preventDefault();
    // Chrome requires returnValue to be set.
    /* eslint no-param-reassign:0 */
    event.returnValue = '';
  });

  document.querySelectorAll('.quash-beforeunload').forEach((element) => {
    element.addEventListener('click', () => {
      document.body.classList.add('quash-beforeunload');
    });

    element.addEventListener('submit', () => {
      document.body.classList.add('quash-beforeunload');
    });
  });
}

registerListeners();
