import msToHumanReadable from './time-helper';

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

  if (Number.isNaN(start) || Number.isNaN(end)) return;

  let text;

  if (now > start && now < end) {
    if (studentStarted) {
      text = `Started ${msToHumanReadable(now - new Date(start)).toString()} ago, ${msToHumanReadable(new Date(end) - now)} remaining.`;
      setGreen(parentElement);
    } else {
      text = `${msToHumanReadable(new Date(end) - now)} left to start`;
      setRed(parentElement);
    }
  } else if (now < start) {
    text = `You can start in ${msToHumanReadable(new Date(start) - now)}`;
    setRed(parentElement);
  } else if (now > end) {
    text = 'The exam window has now passed';
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
