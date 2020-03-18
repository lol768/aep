/* global BUILD_LEVEL:false */
/* eslint-disable global-require */
// Only include in production if BUILD_LEVEL is es5 (the default).
if (BUILD_LEVEL === 'es5') {
  require('./_all');
}
