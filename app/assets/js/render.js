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

import './polyfills';
import './error-reporter-init';
import * as log from './log';
import UploadWithProgress from './upload-with-progress';
import '@universityofwarwick/id7/js/id7-default-feature-detect';
import JDDT from './jddt';
import initAnnouncements from './assessment-announcements';
import initTiming from './assessment-timing';
import showWSConnectivity from './ws-connectivity';


// dynamic import, fire and forget.
/* eslint-ignore no-unused-expressions */
import(/* webpackChunkName: "statuspage-widget" */'@universityofwarwick/statuspage-widget/dist/main').then(() => {
  log.info('statuspage-widget script loaded');
});

// not doing a dynamic import at the moment, since this seems reasonably critical
// (if relevant to the page)

(new UploadWithProgress(document, () => {
  window.location.reload();
}, () => {
  log.warn('Upload failure callback');
})).initialise();

JDDT.initialise(document);

document.addEventListener('DOMContentLoaded', () => {
  if (document.body.classList.contains('connect-ws')) {
    if (document.body.classList.contains('beforeunload')) {
      import('./are-you-sure');
    }
    import('./central-web-socket').then(({ default: websocket }) => {
      initAnnouncements(websocket);
      showWSConnectivity(websocket);
      if (document.querySelector('.timing-information')) initTiming(websocket);
    });
  }

  if (document.querySelectorAll('.undisable-with-checkbox[data-undisable-selector]').length > 0) {
    import('./undisable-with-checkbox');
  }

  if (document.querySelectorAll('.studentAssessmentInfo').length > 0) {
    import('./studentAssessmentInfo');
  }
});
