/* Minimal console wrapper */

const noop = () => {};

/* eslint-disable no-console */
const info = window.console ? console.log : noop;
const error = (window.console && console.error) ? console.error : noop;
const warn = (window.console && console.error) ? console.error : noop;

export { error, warn, info };
export default info;
