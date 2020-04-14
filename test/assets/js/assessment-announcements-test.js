import assessmentAnnouncements from 'assessment-announcements';
import {JSDOM} from 'jsdom';

const dom = new JSDOM('<!DOCTYPE html><html><head></head><body></body></html>');
const document = dom.window.document;
const websock = new WebSocket("ws://whatever");

const messageList = document.createElement("div");
messageList.className = "message-list";
document.body.appendChild(messageList);

describe('Assessment Announcements', () => {

  it('should nicely format a simple message provided by the websocket', () => {
    const message = {
      type: "announcement",
      message: "HONK"
    };

    assessmentAnnouncements(websock);

    websock.send(JSON.stringify(message));


  });

});
