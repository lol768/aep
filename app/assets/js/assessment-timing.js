import msToHumanReadable from './time-helper';

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
