import log from './log';

const RECONNECT_THRESHOLD = 500;

// How often should the websocket send a heartbeat to the server?
const HEARTBEAT_INTERVAL_MS = 30 * 1000; // 30s

const defaultHeartbeat = (ws) => {
  const networkInformation = (window.navigator.connection || {});
  const {
    downlink, downlinkMax, effectiveType, rtt, type,
  } = networkInformation;

  const message = {
    type: 'NetworkInformation',
    data: {
      downlink,
      downlinkMax,
      effectiveType,
      rtt,
      type,
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
   * @param {number} heartBeatInterval Interval at which the onHeartbeat function is called (in ms)
   */
  constructor(endpoint, {
    onConnect, onError, onClose, onData, onHeartbeat, heartBeatInterval,
  } = {}) {
    // polyfill
    if (!endpoint.includes('wss://')) {
      throw new Error(`${endpoint} doesn't look like a valid WebSocket URL`);
    }
    this.endpoint = endpoint;
    this.onConnect = onConnect;
    this.onError = onError;
    this.onClose = onClose;
    this.onData = onData;
    this.onHeartbeat = onHeartbeat || defaultHeartbeat;
    this.heartBeatInterval = heartBeatInterval || HEARTBEAT_INTERVAL_MS;
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
        if (this.onData) {
          this.onData(JSON.parse(d.data));
        }
      }
    };

    if (this.onConnect) {
      ws.addEventListener('open', this.onConnect);
    }

    // Heartbeat
    ws.addEventListener('open', this.sendHeartbeat.bind(this));

    ws.addEventListener('close', (e) => {
      if (this.onClose) {
        this.onClose(e);
      }

      if (this.heartbeatTimeout !== undefined) {
        clearTimeout(this.heartbeatTimeout);
      }

      this.reconnectIfRightTime();
      log('Disconnected from websocket, trying to reconnect');
    });

    const errorHandler = (e) => {
      if (this.onError) {
        this.onError(e);
      }
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
    this.heartbeatTimeout = setTimeout(() => {
      this.onHeartbeat(this.ws);
      this.sendHeartbeat();
    }, this.heartBeatInterval);
  }
}
