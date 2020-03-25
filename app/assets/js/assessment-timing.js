import msToHumanReadable from './time-helper';
import WebSocketConnection from './web-sockets';

const clearWarning = ({ parentElement }) => {
  parentElement.classList.remove('text-danger');
  parentElement.classList.add('text-info');
};

const setWarning = ({ parentElement }) => {
  parentElement.classList.add('text-danger');
  parentElement.classList.remove('text-info');
};

const refresh = (node) => {
  const {
    dataset: {
      rendering,
    },
  } = node;

  const {
    end,
    studentStarted,
    start,
  } = JSON.parse(rendering);

  const now = Number(new Date());

  if (Number.isNaN(start) || Number.isNaN(end)) return;

  let text;

  if (now > start && now < end) {
    if (studentStarted) {
      text = `Started ${msToHumanReadable(now - new Date(start))} ago. ${msToHumanReadable(new Date(end) - now)} remaining.`;
      clearWarning(node);
    } else {
      text = `${msToHumanReadable(new Date(end) - now)} left to start`;
      setWarning(node);
    }
  } else if (now < start) {
    text = `You can start in ${msToHumanReadable(new Date(start) - now)}`;
    setWarning(node);
  } else if (now > end) {
    text = 'The exam window has now passed';
    setWarning(node);
  }

  const textNode = document.createTextNode(text);
  const existingTextNode = node.lastChild;
  if (existingTextNode) {
    node.replaceChild(textNode, existingTextNode);
  } else {
    node.appendChild(textNode);
  }
};

[...document.getElementsByClassName('time-left-to-start')].forEach((node) => {
  refresh(node);
  setInterval(() => {
    refresh(node);
  }, 30000);
});

import('./web-sockets').then(() => {
  const websocket = new WebSocketConnection(`wss://${window.location.host}/websocket`, {
    onConnect: () => {},
    onError: () => {},
    onData: (d) => {
      if (d.type === 'timeRemaining') {
        document.querySelector('.time-remaining').innerHTML = d.timeRemaining;
      }
    },
    onClose: () => {
    },
    onHeartbeat: (ws) => {
      const message = {
        type: 'RequestTimeRemaining',
        data: {
          assessmentId: '12c550df-bd7c-4f13-911a-d947ff9510c2',
        },
      };

      ws.send(JSON.stringify(message));
    },
  });
  websocket.connect();
});
