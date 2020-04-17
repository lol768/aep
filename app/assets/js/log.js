/* Minimal console wrapper */

const noop = () => {};

/* eslint-disable no-console */
const info = (window.console && console.log) ? console.log.bind(console) : noop;
const error = (window.console && console.error) ? console.error.bind(console) : info;
const warn = (window.console && console.warn) ? console.warn.bind(console) : info;

export { error, warn, info };
export default info;
