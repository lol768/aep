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
import UploadWithProgress from './upload-with-progress';

// dynamic import, fire and forget.
/* eslint-ignore no-unused-expressions */
import(/* webpackChunkName: "statuspage-widget" */'@universityofwarwick/statuspage-widget/dist/main').then(() => {
  log('statuspage-widget script loaded');
});

// not doing a dynamic import at the moment, since this seems reasonably critical
// (if relevant to the page)

(new UploadWithProgress(document, () => {
  log('Upload success callback');
}, () => {
  log('Upload failure callback');
})).initialise();
