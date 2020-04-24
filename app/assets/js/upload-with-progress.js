import log from './log';

// These are fallbacks only in case we don't get an intelligible short error message back
const STATUS_ERRORS = {
  413: 'One or more of your files is too large. Please compress the file or break it up into multiple files and upload separately',
  415: 'The file format of one or more of your files is not supported. You may need to convert it to a PDF',
  422: 'We couldn\'t upload your submission. Please try again.',
  403: 'The upload failed because it looks like you might have been logged out. Do you want to refresh the page and try again?',
};

function addEventListenerOnce(element, event, fn) {
  const func = () => {
    element.removeEventListener(event, func);
    fn();
  };
  element.addEventListener(event, func);
}

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
  static handleErrorInUpload(formElement, errorMessage, contentType, statusCode) {
    let errorToDisplay = '';

    try {
      const parsedJson = JSON.parse(errorMessage);
      errorToDisplay = parsedJson.errors[0].message;
    } catch (e) {
      if (contentType !== null && contentType.indexOf('text/html') === -1) {
        errorToDisplay = errorMessage;
      } else if (statusCode in STATUS_ERRORS) {
        errorToDisplay = STATUS_ERRORS[statusCode];
      } else {
        errorToDisplay = 'We couldn\'t upload your submission. Please try again.';
      }
    }

    log('Failed to finish upload');
    formElement.querySelector('.upload-info').classList.add('hide'); // IE10
    formElement.querySelector('.upload-error').classList.remove('hide'); // IE10
    if (errorToDisplay && errorToDisplay !== '') {
      // We check status code zero due to CSP violation on HTTP 303 redirect
      // which is to a non-connect-src whitelisted URI.
      /* eslint no-alert: 0 */
      const authIssue = statusCode === 403 || statusCode === 303 || statusCode === 0;
      if (authIssue && window.confirm(STATUS_ERRORS[403])) {
        window.location.reload();
      }
      /* eslint-disable-next-line no-param-reassign */
      formElement.querySelector('.upload-error-text').innerText = errorToDisplay;
    }
    formElement.reset();
  }

  /**
   * @private
   */
  attachFormListeners(element) {
    element.setAttribute('data-attached', true);
    UploadWithProgress.setDisabledByDefault(element);
    element.addEventListener('submit', (formSubmitEvent) => {
      const formElement = formSubmitEvent.target;

      if (this.attemptLogger !== undefined) {
        try {
          this.attemptLogger.logAttempt(formElement.getAttribute('data-student-assessment-id'));
        } catch (e) {
          log('Attempt log failed, ignoring as there are more pressing matters', e);
        }
      }
      formSubmitEvent.preventDefault(); // don't want form to submit the form normally

      try {
        const submitBtns = formElement.parentElement.querySelectorAll('[type=submit]');
        const cancelBtns = formElement.parentElement.querySelectorAll('[data-action="cancelUpload"]');
        let cancelledByUser = false;
        const xhr = new XMLHttpRequest();
        xhr.withCredentials = true;
        // IE 10
        const formData = new FormData(formElement);
        formData.append('xhr', 'true');

        // register progress callback, IE10+ (https://caniuse.com/#feat=xhr2)
        xhr.upload.addEventListener('progress', (ev) => {
          // ev is instanceof ProgressEvent
          const progress = ev.loaded / ev.total;
          formElement.querySelector('progress').value = Math.round(progress * 100);
        });
        xhr.addEventListener('readystatechange', () => {
          if (xhr.readyState === XMLHttpRequest.DONE) {
            formElement.querySelector('.upload-info').classList.add('hide'); // IE10
            submitBtns.forEach((e) => UploadWithProgress.undisableButton(e));
            if (xhr.status === 200) {
              this.successCallback(formElement);
            } else if (!cancelledByUser) {
              this.failureCallback(xhr);
              UploadWithProgress.handleErrorInUpload(formElement, xhr.responseText, xhr.getResponseHeader('Content-Type'), xhr.status);
            }
          } else if (xhr.readyState === XMLHttpRequest.OPENED) {
            formElement.querySelector('.upload-info').classList.remove('hide'); // IE10
          }
        });
        xhr.addEventListener('error', () => {
          submitBtns.forEach((e) => UploadWithProgress.undisableButton(e));
          this.failureCallback(xhr);
          UploadWithProgress.handleErrorInUpload(formElement, xhr.responseText, xhr.getResponseHeader('Content-Type'), xhr.status);
        });
        // start async xhr
        xhr.open('POST', formElement.getAttribute('action'), true);
        xhr.setRequestHeader('OnlineExams-Upload', 'true');
        xhr.send(formData);
        submitBtns.forEach((e) => UploadWithProgress.disableButtonWithTitle(e, 'Please wait for the file to be uploaded'));
        cancelBtns.forEach((e) => e.classList.remove('hide'));
        cancelBtns.forEach((el) => addEventListenerOnce(el, 'click', () => {
          cancelledByUser = true;
          xhr.abort();

          // Fire and forget
          const id = formElement.getAttribute('data-student-assessment-id');
          const cancelXhr = new XMLHttpRequest();
          cancelXhr.open('POST', '/api/log-upload-cancelled', true);
          cancelXhr.setRequestHeader('Content-Type', 'application/json');
          cancelXhr.send(`{"id":"${id}"}`);

          submitBtns.forEach((e) => UploadWithProgress.undisableButton(e));
          formElement.querySelector('.upload-info').classList.add('hide');
          cancelBtns.forEach((e) => e.classList.add('hide'));
        }));
      } catch (e) {
        // if all else fails, just submit with a normal POST
        formElement.submit();
      }
      return false;
    });
  }

  static setDisabledByDefault(targetElement) {
    const submitBtns = targetElement.querySelectorAll('[type=submit]');
    submitBtns.forEach((e) => UploadWithProgress.disableButtonWithTitle(e));
    const fileList = targetElement.querySelector('input[type=file]');
    fileList.addEventListener('change', () => {
      if (fileList.files.length !== 0) {
        submitBtns.forEach((e) => UploadWithProgress.undisableButton(e));
      } else {
        submitBtns.forEach((e) => UploadWithProgress.disableButtonWithTitle(e));
      }
    });
  }

  static undisableButton(submitBtn) {
    submitBtn.removeAttribute('disabled');
    submitBtn.removeAttribute('title');
  }

  static disableButtonWithTitle(submitBtn, title = 'Please pick at least one file') {
    submitBtn.setAttribute('disabled', 'disabled');
    submitBtn.setAttribute('title', title);
  }

  /**
   * @private
   */
  registerEventListeners(targetElement) {
    // polyfill - NodeList#forEach
    targetElement.querySelectorAll('form.upload-progress:not([data-attached])').forEach((el) => {
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
    if (typeof MutationObserver !== 'undefined') {
      // Should exist in IE11
      const observer = new MutationObserver((objects) => {
        // expect Babel for this
        objects.forEach((mutationRecord) => {
          if (mutationRecord.type === 'childList') {
            for (let i = 0; i < mutationRecord.target.children.length; i += 1) {
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

  setAttemptLogger(attemptLogger) {
    this.attemptLogger = attemptLogger;
  }
}
