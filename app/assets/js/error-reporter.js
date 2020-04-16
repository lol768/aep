/* eslint-env browser */

/**
 * Catches Javascript errors and sends them to the
 * server for reporting purposes.
 */

import { throttle } from 'lodash-es';
import * as log from './log';

let errors = [];

const postErrorsThrottled = throttle(() => {
  const errorsToPost = errors;
  fetch(
    '/api/errors/js',
    {
      method: 'POST',
      body: JSON.stringify(errorsToPost),
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
    },
  ).then((response) => {
    errors = errors.slice(errorsToPost.length);

    if (response.status === 400) {
      log.info('Errors rejected by server');
      errors = errors.concat({
        time: new Date().getTime(),
        message: 'Errors rejected by server',
        pageUrl: window.location.href,
      });
      postErrorsThrottled();
      return;
    }

    log.info('Errors posted to server');
  }).catch((e) => {
    log.info('Failed to post errors to server', e);
    postErrorsThrottled();
  });
}, 5000);

function onError(message, source, line, column, error) {
  let stack = error.stack || error;

  if (typeof stack !== 'string') {
    stack = JSON.stringify(stack);
  }

  errors = errors.concat({
    time: new Date().getTime(),
    message,
    source,
    line,
    column,
    stack,
    pageUrl: window.location.href,
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
