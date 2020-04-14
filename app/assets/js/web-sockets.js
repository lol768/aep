import log from './log';
import { browserLocalTimezoneName } from './jddt';

/*

  If you're importing this directly, you probably want to stop, collaborate and instead import
  central-web-socket which handles connecting a single instance of the connection for you.

 */

const RECONNECT_THRESHOLD = 500;

// How often should the websocket send a heartbeat to the server?
const HEARTBEAT_INTERVAL_MS = 30 * 1000; // 30s

const defaultHeartbeat = (ws) => {
  const networkInformation = (window.navigator.connection || {});
  const {
    downlink, downlinkMax, effectiveType, rtt, type,
  } = networkInformation;

  const inProgressAssessmentElement = document.querySelector('.in-progress-assessment-data');
  let studentAssessmentId = null;
  if (inProgressAssessmentElement) {
    studentAssessmentId = inProgressAssessmentElement.getAttribute('data-id');
  }

  const localTimezoneName = browserLocalTimezoneName();

  const message = {
    type: 'NetworkInformation',
    data: {
      downlink,
      downlinkMax,
      effectiveType,
      rtt,
      type,
      studentAssessmentId,
      localTimezoneName,
    },
  };

  ws.send(JSON.stringify(message));
};

export default class WebSocketConnection {
  /**
   * @param {string} endpoint Endpoint URI: e.g. wss//:warwick.ac.uk/path
   * @param {function} onConnect Callback for when the connection has been established.
   * @param {function} onError Callback for if there is an error with the websocket.
   * @param {function} onClose Callback for if connection is closed.
   * @param {function} onData Callback for when JSON data is received.
   * @param {function} onHeartbeat Function called at regular intervals to send messages to server
   */
  constructor(endpoint, {
    onConnect, onError, onClose, onData, onHeartbeat,
  } = {}) {
    // polyfill
    if (!endpoint.includes('wss://')) {
      throw new Error(`${endpoint} doesn't look like a valid WebSocket URL`);
    }
    this.endpoint = endpoint;
    this.onConnect = onConnect ? [onConnect] : [];
    this.onError = onError ? [onError] : [];
    this.onClose = onClose ? [onClose] : [];
    this.onData = onData ? [onData] : [];
    this.onHeartbeat = onHeartbeat ? [defaultHeartbeat, onHeartbeat] : [defaultHeartbeat];
  }

  add({
    onConnect, onError, onClose, onData, onHeartbeat,
  } = {}) {
    if (onConnect) this.onConnect.push(onConnect);
    if (onError) this.onError.push(onError);
    if (onClose) this.onClose.push(onClose);
    if (onData) this.onData.push(onData);
    if (onHeartbeat) this.onHeartbeat.push(onHeartbeat);
  }

  connect() {
    this.timeout = undefined;
    this.heartbeatTimeout = undefined;
    if (this.ws !== undefined) {
      if (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING) {
        // we don't need to reconnect
        return;
      }
      this.ws.close();
    }
    log(`Connecting to ${this.endpoint}`);
    const ws = new WebSocket(this.endpoint);
    this.ws = ws;

    ws.onmessage = (d) => {
      if ('data' in d) {
        this.onData.forEach((onData) => onData(JSON.parse(d.data)));
      }
    };

    this.onConnect.forEach((onConnect) => ws.addEventListener('open', onConnect));

    // Heartbeat
    ws.addEventListener('open', this.sendHeartbeat.bind(this));

    ws.addEventListener('close', (e) => {
      this.onClose.forEach((onClose) => onClose(e));

      if (this.heartbeatTimeout !== undefined) {
        clearTimeout(this.heartbeatTimeout);
      }

      this.reconnectIfRightTime();
      log('Disconnected from websocket, trying to reconnect');
    });

    const errorHandler = (e) => {
      this.onError.forEach((onError) => onError(e));
      ws.close();
      this.reconnectIfRightTime();
      log('Disconnected from websocket, trying to reconnect');
    };
    ws.addEventListener('error', errorHandler);
    this.lastConnectAttempt = (new Date()).getTime();
  }

  reconnectIfRightTime() {
    const now = (new Date()).getTime();
    if (this.timeout !== undefined) {
      return;
    }

    const hasNotConnectedBefore = this.lastConnectAttempt === undefined;
    if (hasNotConnectedBefore || (now - this.lastConnectAttempt) > RECONNECT_THRESHOLD) {
      log('Enough time has passed for a reconnect event');
      this.connect();
    } else {
      log(`Scheduling reconnect for ${RECONNECT_THRESHOLD}ms from now`);
      this.timeout = setTimeout(() => {
        this.connect();
      }, RECONNECT_THRESHOLD);
    }
  }

  sendHeartbeat() {
    this.onHeartbeat.forEach((onHeartbeat) => onHeartbeat(this.ws));
    this.heartbeatTimeout = setTimeout(() => {
      this.sendHeartbeat();
    }, HEARTBEAT_INTERVAL_MS);
  }
}
