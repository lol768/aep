import {initAnnouncements, formatAnnouncement} from 'assessment-announcements';
import {JSDOM} from 'jsdom';

const dom = new JSDOM('<!DOCTYPE html><html><head></head><body></body></html>');
const document = dom.window.document;

const messageList = document.createElement("div");
messageList.className = "message-list";
document.body.appendChild(messageList);

describe('Assessment Announcements', () => {

  it('should nicely format a simple message provided by the websocket', () => {
    const messageData = {
      message: "This is the captain of your ship \n calling...",
      timestamp: 1586850896285
    };

    const expectedFormat = '<div class="media-left"><i aria-hidden="true" class="fad fa-bullhorn"></i></div><div class="media-body">This is the captain of your ship <br> calling...<div class="query-time">1586850896285</div></div>'
    const formattedMessage = formatAnnouncement(messageData);
    expect(formattedMessage.tagName).equals('DIV');
    expect(formattedMessage.className).equals('alert alert-info media');
    expect(formattedMessage.innerHTML).equals(expectedFormat);
  });

});
