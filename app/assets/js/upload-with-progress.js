import log from './log';

/**
 * Component which allows for file upload forms to be
 * sent via AJAX XHR, with built-in progress indicator
 * and error/completion handling.
 */
export default class UploadWithProgress {
  /**
   * Sets up UploadWithProgress support for the provided DOM element container.
   *
   * The form element should contain:
   *  - An action attribute
   *  - A .upload-info.hide element with a <progress> child
   *  - A .upload-error.hide element, to be shown when the XHR fails
   *
   * @param {Node} container
   * @param {function(Node)} successCallback
   * @param {function(XMLHttpRequest)} failureCallback
   */
  constructor(container, successCallback, failureCallback) {
    this.container = container;
    this.successCallback = successCallback;
    this.failureCallback = failureCallback;
  }

  /**
   * @private
   */
  static handleErrorInUpload(formElement) {
    log('Failed to finish upload');
    // TODO log to server
    formElement.querySelector('.upload-info').classList.add('hide'); // IE10
    formElement.querySelector('.upload-error').classList.remove('hide'); // IE10
    formElement.reset();
  }

  /**
   * @private
   */
  attachFormListeners(element) {
    log('Attaching form listeners to element', element);
    element.setAttribute('data-attached', true);
    element.addEventListener('submit', (formSubmitEvent) => {
      const formElement = formSubmitEvent.target;
      formSubmitEvent.preventDefault(); // don't want form to submit the form normally

      try {
        const xhr = new XMLHttpRequest();
        // IE 10
        const formData = new FormData(formElement);

        // register progress callback, IE10+ (https://caniuse.com/#feat=xhr2)
        xhr.upload.addEventListener('progress', (ev) => {
          // ev is instanceof ProgressEvent
          const progress = ev.loaded / ev.total;
          formElement.querySelector('progress').value = Math.round(progress * 100);
        });
        xhr.addEventListener('readystatechange', () => {
          if (xhr.readyState === XMLHttpRequest.DONE) {
            formElement.querySelector('.upload-info').classList.add('hide'); // IE10
            if (xhr.status === 200) {
              this.successCallback(formElement);
            } else {
              this.failureCallback(xhr);
              UploadWithProgress.handleErrorInUpload(formElement);
            }
          } else if (xhr.readyState === XMLHttpRequest.OPENED) {
            formElement.querySelector('.upload-info').classList.remove('hide'); // IE10
          }
        });
        xhr.addEventListener('error', () => {
          this.failureCallback(xhr);
          UploadWithProgress.handleErrorInUpload(formElement);
        });
        // start async xhr
        xhr.open('POST', formElement.getAttribute('action'), true);
        xhr.send(formData);
      } catch (e) {
        // if all else fails, just submit with a normal POST
        formElement.submit();
      }
      return false;
    });
  }

  /**
   * @private
   */
  registerEventListeners(targetElement) {
    // polyfill - NodeList#forEach
    targetElement.querySelectorAll('form.upload-progress:not([data-attached])').forEach((el) => {
      log('Found form candidate');
      this.attachFormListeners(el);
    });
  }

  /**
   * Initialises the component on the container provided in the constructor.
   * In browsers which support it, a MutationObserver will be used to
   * automatically set up support for forms injected into the page
   * dynamically.
   */
  initialise() {
    log('initialise() in UploadWithProgress');
    if (typeof MutationObserver !== 'undefined') {
      // Should exist in IE11
      const observer = new MutationObserver((objects) => {
        // expect Babel for this
        objects.forEach((mutationRecord) => {
          if (mutationRecord.type === 'childList') {
            log('Found MutationRecord');
            for (let i = 0; i < mutationRecord.target.children.length; i += 1) {
              log('Found child', mutationRecord.target.children[i]);
              this.registerEventListeners(mutationRecord.target.children[i]);
            }
          }
        });
      });
      observer.observe(this.container, {
        childList: true,
        subtree: true,
      });
    } else {
      // IE9 +
      document.addEventListener('DOMContentLoaded', () => {
        this.registerEventListeners(this.container);
      });
    }
  }
}
