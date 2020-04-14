import WebSocketConnection from './web-sockets';

const websocket = new WebSocketConnection(`wss://${window.location.host}/websocket`);
websocket.connect();

export default websocket;
