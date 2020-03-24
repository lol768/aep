function handleChange(el, target) {
  if (el.checked) {
    target.removeAttribute('disabled');
  } else {
    target.setAttribute('disabled', 'disabled');
  }
}

// Never used in anything dynamically added to the DOM
function registerEventListeners(targetElement) {
  targetElement.querySelectorAll('[data-undisable-selector]').forEach((el) => {
    let target = document.querySelector(el.getAttribute('data-undisable-selector'));
    if (target) {
      handleChange(el, target);
    }
    el.addEventListener('change', () => {
      // DOM may have changed
      target = document.querySelector(el.getAttribute('data-undisable-selector'));
      if (target) {
        handleChange(el, target);
      }
    });
  });
}

registerEventListeners(document.body);
