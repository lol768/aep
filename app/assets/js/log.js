/* Minimal console wrapper */

const noop = () => {};

/* eslint-disable no-console */
const info = (window.console && console.info) ? console.log : noop;
const error = (window.console && console.error) ? console.error : info;
const warn = (window.console && console.warn) ? console.error : info;

export { error, warn, info };
export default info;
