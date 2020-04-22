import log from './log';

/**
 * Class which can be used in conjunction with an existing
 * submit handler in order to log file upload attempts to the
 * server.
 */
export default class FileUploadAttemptLogger {
  /**
   * @param {HTMLFormElement} formElement
   * @param {WebSocketConnection} websocket
   */
  constructor(formElement, websocket) {
    this.formElement = formElement;
    this.websocket = websocket;
  }

  logAttempt(studentAssessmentId) {
    this.buildData(studentAssessmentId).then((data) => {
      const payload = JSON.stringify(data);
      if ('sendBeacon' in navigator) {
        navigator.sendBeacon('/api/log-upload-attempt',
          new Blob([payload], { type: 'application/json' }));
      } else {
        // Fire and forget
        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/log-upload-attempt', true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.send(payload);
      }

      if (this.websocket) {
        this.websocket.send(JSON.stringify({
          type: 'UploadAttempt',
          data,
        }));
      }
    }).catch(() => {
      log('Problem building data for file upload attempt');
    });
  }

  buildData(studentAssessmentId) {
    const fileInput = this.formElement.querySelector('input[type=file]');
    if (!fileInput) {
      log('Cannot log upload attempt, no valid file input element located');
      return Promise.reject(new Error('No valid files input'));
    }
    /** @type {FileList} files */
    const { files } = fileInput;
    const len = files.length;
    let i = 0;
    const results = [];
    const promiseList = [];

    while (i < len) {
      const file = files[i];
      const jsonObj = {};
      // Browser support is poor in IE - we have to use lastModifiedDate
      if ('lastModified' in file) {
        jsonObj.lastModified = file.lastModified;
      } else if ('lastModifiedDate' in file) {
        jsonObj.lastModified = file.lastModifiedDate.getDate();
      }
      jsonObj.name = file.name;
      jsonObj.mimeType = file.type;
      jsonObj.size = file.size;
      jsonObj.headerHex = '';
      if (file.size > 10) {
        const sliced = file.slice(0, 10);
        promiseList.push(sliced.arrayBuffer().then((ab) => {
          jsonObj.headerHex = Array.prototype.map.call(
            new Uint8Array(ab),
            (x) => (`00${x.toString(16)}`).slice(-2),
          ).join('');
        }));
      }
      i += 1;
      results.push(jsonObj);
    }

    return Promise.all(promiseList).then(() => ({
      files: results,
      studentAssessmentId,
      source: '',
    }));
  }
}
