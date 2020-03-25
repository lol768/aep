import { error } from './log';

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
    const selector = el.getAttribute('data-undisable-selector');
    let target = document.querySelector(selector);
    if (target) {
      handleChange(el, target);
    } else {
      error(`undisable-with-checkbox: Target with selector ${selector} was unavailable at start-up`);
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
