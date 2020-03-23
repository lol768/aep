import * as countdown from 'countdown';

const setGreen = (el) => {
  el.classList.remove('text-danger');
  el.classList.add('text-info');
};

const setRed = (el) => {
  el.classList.add('text-danger');
  el.classList.remove('text-info');
};

const refresh = (node) => {
  const {
    parentElement,
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

  if (Number.isNaN(end) || Number.isNaN(end)) return;

  let text;

  if (now < end && now > start) {
    if (studentStarted) {
      text = `Started ${countdown(null, new Date(start), 220).toString()} ago, ${countdown(null, new Date(end), 220).toString()} remaining.`;
      setGreen(parentElement);
    } else {
      text = `${countdown(null, new Date(end), 220).toString()} left to start`;
      setRed(parentElement);
    }
  } else if (now < start) {
    text = `You can start in ${countdown(null, new Date(start), 220).toString()}`;
    setRed(parentElement);
  } else if (now > end) {
    text = 'You missed the exam, please contact your department';
    setRed(parentElement);
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
