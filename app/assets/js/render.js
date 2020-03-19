/* eslint-env browser */
/*
 * Entrypoint for the frontend section of the app.
 *
 * render.js rules
 *  - no ID7
 *  - no jQuery
 *  - minimal libraries (let's see if we can do without moment-timezone)
 *  - if it isn't essential for page, load dynamically (import() call returning Promise)
 */

import log from './log';
import jddt from './jddt';

// dynamic import, fire and forget.
/* eslint-ignore no-unused-expressions */
import(/* webpackChunkName: "statuspage-widget" */'@universityofwarwick/statuspage-widget/dist/main').then(() => {
  log('statuspage-widget script loaded');

  // Find any <time> elements with a class of 'jddt' and fill them with a local date based on
  // a 'millis' attribute if present.
  document.querySelectorAll('time.jddt').forEach((timeElement) => {
    if (timeElement.hasAttribute('millis')) {
      // eslint-disable-next-line no-param-reassign
      timeElement.innerText = jddt(parseInt(timeElement.getAttribute('millis'), 10)).longLocal();
    }
  });
});
