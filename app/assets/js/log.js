/* Minimal console wrapper */
/* eslint-disable no-console */

export function info(...args) {
  if (window.console && console.info) console.info(...args);
  else if (window.console && console.log) console.log(...args);
}

export function error(...args) {
  if (window.console && console.error) console.error(...args);
  else info(...args);
}

export function warn(...args) {
  if (window.console && console.warn) console.warn(...args);
  else info(...args);
}

export default info;
