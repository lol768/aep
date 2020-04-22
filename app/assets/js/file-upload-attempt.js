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
    const data = this.buildData(studentAssessmentId);
    if (data === null) {
      log('Problem building data for file upload attempt');
      return;
    }

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

    if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
      this.websocket.send(payload);
    }
  }

  buildData(studentAssessmentId) {
    const fileInput = this.formElement.querySelector('input[type=file]');
    if (!fileInput) {
      log('Cannot log upload attempt, no valid file input element located');
      return null;
    }
    /** @type {FileList} files */
    const { files } = fileInput;
    const len = files.length;
    let i = 0;
    const results = [];

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
      i += 1;
      results.push(jsonObj);
    }

    return {
      files: results,
      studentAssessmentId,
    };
  }
}
