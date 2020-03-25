/* eslint-env browser */

/**
 * Catches Javascript errors and sends them to the
 * server for reporting purposes.
 */

import {
  throttle as _throttle,
} from 'lodash-es';
import * as log from './log';

let errors = [];

function isIE() {
  const ua = window.navigator.userAgent;
  const msie = ua.indexOf('MSIE ');
  const ie = (msie > 0 || !!navigator.userAgent.match(/Trident.*rv:11\./));
  if (ie) {
    log.warn('Running on MS IE');
  }
  return ie;
}

export const addQsToUrl = (url, qs) => {
  const parsedUrl = new URL(url);
  Object.entries(qs).forEach(([key, value]) => {
    parsedUrl.searchParams.set(key, value);
  });
  return parsedUrl.href;
};

const postErrorsThrottled = _throttle(() => {
  const errorsToPost = errors;
  const url = '/api/errors/js';
  fetch(
    isIE() ? addQsToUrl(url, {
      ts: Number(new Date()),
    }) : url,
    {
      method: 'POST',
      body: JSON.stringify(errorsToPost),
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
    },
  ).then(() => {
    log.info('Errors posted to server');
    errors = errors.slice(errorsToPost.length);
  }).catch((e) => {
    log.info('Failed to post errors to server', e);
    postErrorsThrottled();
  });
}, 5000);

function onError(message, source, line, column, error) {
  errors = errors.concat({
    time: new Date().getTime(),
    message,
    source,
    line,
    column,
    stack: error.stack || error,
    pageUrl: window.location.href, // page url
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
