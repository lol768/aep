import log from './log';
import { browserLocalTimezoneName } from './jddt';

/*

  If you're importing this directly, you probably want to stop, collaborate and instead import
  central-web-socket which handles connecting a single instance of the connection for you.

 */

/**
 * @typedef {function} PlainEventHandler
 * @return {void}
 */

/**
 * @typedef {function} DataEventHandler
 * @param {object} data
 * @return {void}
 */

/**
 * @typedef {function} ErrorEventHandler
 * @param {Error} error
 * @return {void}
 */

/**
 * @typedef {function} SocketEventHandler
 * @param {WebSocket} socket
 * @return {void}
 */

/**
 * @typedef {Object} Callbacks
 * @property {PlainEventHandler} [onConnect] - When the connection has been established.
 * @property {ErrorEventHandler} [onError] - If there is an error with the websocket.
 * @property {PlainEventHandler} [onClose] - If connection is closed.
 * @property {DataEventHandler} [onData] - When JSON data is received.
 * @property {SocketEventHandler} [onHeartbeat] - Called at regular intervals to send messages
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
  let assessmentId = null;
  let usercode = null;
  if (inProgressAssessmentElement) {
    studentAssessmentId = inProgressAssessmentElement.getAttribute('data-id');
    assessmentId = inProgressAssessmentElement.getAttribute('data-assessment');
    usercode = inProgressAssessmentElement.getAttribute('data-usercode');
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
      assessmentId,
      usercode,
      localTimezoneName,
    },
  };

  ws.send(JSON.stringify(message));
};

export default class WebSocketConnection {
  /**
   * @param {string} endpoint Endpoint URI: e.g. wss//:warwick.ac.uk/path
   * @param {Callbacks} callbacks
   */
  constructor(endpoint, callbacks = {}) {
    // polyfill
    if (!endpoint.includes('wss://')) {
      throw new Error(`${endpoint} doesn't look like a valid WebSocket URL`);
    }
    const {
      onConnect, onError, onClose, onData, onHeartbeat,
    } = callbacks;
    this.endpoint = endpoint;
    /** @type {PlainEventHandler[]} */
    this.onConnect = onConnect ? [onConnect] : [];
    /** @type {ErrorEventHandler[]} */
    this.onError = onError ? [onError] : [];
    /** @type {PlainEventHandler[]} */
    this.onClose = onClose ? [onClose] : [];
    /** @type {DataEventHandler[]} */
    this.onData = onData ? [onData] : [];
    /** @type {SocketEventHandler[]} */
    this.onHeartbeat = onHeartbeat ? [defaultHeartbeat, onHeartbeat] : [defaultHeartbeat];
    /** @type {?number} */
    this.dataLastReceivedAt = null;
    /** @type {?WebSocket} */
    this.ws = undefined;
  }

  /**
   * @param {Callbacks} callbacks
   */
  add(callbacks = {}) {
    const {
      onConnect, onError, onClose, onData, onHeartbeat,
    } = callbacks;
    if (onConnect) {
      this.onConnect.push(onConnect);

      if (this.ws !== undefined) {
        if (this.ws.readyState === WebSocket.OPEN) {
          onConnect();
        } else {
          this.ws.addEventListener('open', onConnect);
        }
      }
    }
    if (onError) this.onError.push(onError);
    if (onClose) this.onClose.push(onClose);
    if (onData) this.onData.push(onData);
    if (onHeartbeat) this.onHeartbeat.push(onHeartbeat);
  }

  connect() {
    this.timeout = undefined;
    this.heartbeatTimeout = undefined;
    this.dataLastReceivedAt = null;

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
        this.dataLastReceivedAt = Date.now();
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
    if (this.dataLastReceivedAt != null
      && this.dataLastReceivedAt < Date.now() - HEARTBEAT_INTERVAL_MS) {
      log('No data received in the last heartbeat window');

      // IE11
      let event;
      if (typeof Event === 'function') {
        event = new Event('error');
      } else {
        event = document.createEvent('Event');
        event.initEvent('error', true, true);
      }
      this.ws.dispatchEvent(event);

      return;
    }

    this.onHeartbeat.forEach((onHeartbeat) => onHeartbeat(this.ws));
    this.heartbeatTimeout = setTimeout(() => {
      this.sendHeartbeat();
    }, HEARTBEAT_INTERVAL_MS);
  }
}
