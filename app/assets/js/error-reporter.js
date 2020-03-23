/* eslint-env browser */

/**
 * Catches Javascript errors and sends them to the
 * server for reporting purposes.
 */

import log from 'loglevel';
import _ from 'lodash-es';
import {postJsonWithCredentials} from '@universityofwarwick/serverpipe';

let errors = [];
let postErrorsThrottled;

function postErrors() {
  const errorsToPost = errors;

  postJsonWithCredentials('/api/errors/js', errorsToPost)
    .then(() => {
      log.info('Errors posted to server');
      errors = errors.slice(errorsToPost.length);
    }).catch((e) => {
    log.warn('Failed to post errors to server', e);
    postErrorsThrottled();
  });
}

postErrorsThrottled = _.throttle(postErrors, 5000); // eslint-disable-line prefer-const

function onError(message, source, line, column, error) {
  errors = errors.concat({
    time: new Date().getTime(),
    message,
    source,
    line,
    column,
    stack: error.stack || error,
  });

  postErrorsThrottled();
}

export function post(e) {
  onError(e.message, null, null, null, e);
}

export default function init() {
  window.onerror = onError;
  if (window.addEventListener) {
    window.addEventListener('unhandledrejection', (e) => {
      // e: https://developer.mozilla.org/en-US/docs/Web/API/PromiseRejectionEvent
      log.error('Unhandled promise rejection', e);
      const message = (e.reason && e.reason.message) ? ` (${e.reason.message})` : '';
      onError(`Unhandled promise rejection: ${e.reason}${message}`, null, null, null, e.reason);
      e.preventDefault();
    });
  }
}
