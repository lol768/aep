import log from './log';

export default class WebSocketConnection {
  /**
   * @param {string} endpoint Endpoint URI: e.g. wss//:warwick.ac.uk/path
   * @param {function} onConnect Callback for when the connection has been established.
   * @param {function} onError Callback for if there is an error with the websocket.
   * @param {function} onClose Callback for if connection is closed.
   * @param {function} onData Callback for when JSON data is received.
   */
  constructor(endpoint, {
    onConnect, onError, onClose, onData,
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
  }

  connect() {
    const ws = new WebSocket(this.endpoint);
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
    if (this.onClose) {
      ws.addEventListener('close', this.onClose);
    }
    const errorHandler = (e) => {
      if (this.onError) {
        this.onError(e);
      }
      this.connect();
      log('Disconnected from websocket, trying to reconnect');
    };
    ws.addEventListener('error', errorHandler);
  }
}
