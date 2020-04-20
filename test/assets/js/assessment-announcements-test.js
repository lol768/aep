import {formatAnnouncement, handleData} from 'assessment-announcements';
import {JSDOM} from 'jsdom';

const dom = new JSDOM('<!DOCTYPE html><html><head></head><body></body></html>');
const window = dom.window;
const document = window.document;

const messageList = document.createElement('div');
messageList.className = 'message-list';
document.body.appendChild(messageList);

describe('Assessment Announcements', () => {

  beforeEach(() => {
    messageList.innerHTML = '';
  });

  it('should nicely format a simple message provided by the websocket', () => {
    const messageData = {
      id: '1234',
      assessmentId: '06cce5db-95c8-4710-8888-05f5a8c3c28d',
      messageText: 'This is the captain of your ship, \n Colin...',
      messageHTML: 'This is the captain of your ship, <br> Colin...',
      timestamp: 1586850896285
    };

    const expectedFormat = '<div class="alert alert-info media" data-announcement-id="1234"><div class="media-left"><i aria-hidden="true" class="fad fa-bullhorn"></i></div><div class="media-body">This is the captain of your ship, <br> Colin...<div class="query-time">1586850896285</div></div></div>'
    const formattedMessage = formatAnnouncement(messageData);
    expect(formattedMessage.outerHTML).equals(expectedFormat);
  });

  it('should only show announcements appropriate to the assessmentId', () => {
    const goodId = '06cce5db-95c8-4710-8888-05f5a8c3c28d';
    const badId = 'fd48de8e-67fa-4c0c-b337-292f006b480e';

    const goodMessage = {
      type: 'announcement',
      id: '1234',
      assessmentId: goodId,
      messageText: 'Very small radiator you came from afar',
      messageHTML: 'Very small radiator you came from afar',
      timestamp: 1586850896285
    };

    const badMessage = {
      type: 'announcement',
      id: '5678',
      assessmentId: badId,
      messageText: 'I woke up this one day - I was a cowboy',
      messageHTML: 'I woke up this one day - I was a cowboy',
      timestamp: 1586850896285
    };

    handleData(goodMessage, goodId, messageList);
    handleData(badMessage, goodId, messageList);

    // Only the goodMessage should show on the page

    expect(messageList.childNodes.length).to.equal(1);
    expect(messageList.innerHTML.indexOf(goodMessage.messageHTML) > -1);
    expect(messageList.innerHTML.indexOf(badMessage.messageHTML) === -1);
  });

});
