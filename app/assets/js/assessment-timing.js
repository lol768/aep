import msToHumanReadable from './time-helper';

const clearWarning = ({ parentElement }) => {
  parentElement.classList.remove('text-danger');
  parentElement.classList.add('text-info');
};

const setWarning = ({ parentElement }) => {
  parentElement.classList.add('text-danger');
  parentElement.classList.remove('text-info');
};

const markParentForm = (node, data) => {
  const form = node.closest('form');
  if (!form) return;
  const submitBtn = form.querySelector('.btn[type=submit]');
  if (!submitBtn) return;
  if (data.timeUntilStart > 0) {
    submitBtn.classList.add('hide');
  } else if (data.timeUntilEndOfWindow > 0) {
    submitBtn.classList.remove('hide');
  }
};

const updateTimingInfo = (node, data) => {
  let text;
  markParentForm(node, data);
  if (data.hasStarted && !data.hasFinalised) {
    text = `Started ${msToHumanReadable(data.timeSinceStart)} ago.`;
    if (data.timeRemaining > 0) {
      text += ` ${msToHumanReadable(data.timeRemaining)} remaining`;
      if (data.extraTimeAdjustment) {
        text += ` (including ${msToHumanReadable(data.extraTimeAdjustment)} additional time)`;
      }
      text += '.';
      clearWarning(node);
    } else {
      text += `\nExceeded deadline by ${msToHumanReadable(-data.timeRemaining)}.`;
      setWarning(node);
    }
  } else if (data.timeUntilStart > 0) {
    text = `You can start in ${msToHumanReadable(data.timeUntilStart)}.`;
    setWarning(node);
  } else if (data.timeUntilEndOfWindow > 0) {
    text = `${msToHumanReadable(data.timeUntilEndOfWindow)} left to start.`;
    setWarning(node);
  } else {
    text = 'The exam window has now passed.';
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

const offlineRefresh = (node) => {
  const {
    dataset: {
      rendering,
    },
  } = node;

  const {
    windowStart,
    windowEnd,
    start,
    end,
    hasStarted,
    hasFinalised,
    extraTimeAdjustment,
  } = JSON.parse(rendering);

  const now = Number(new Date());

  const hasWindowPassed = now > windowEnd;
  const inProgress = hasStarted && !hasFinalised;
  const notYetStarted = !hasStarted && !hasWindowPassed;
  const extraTime = extraTimeAdjustment || 0;
  const timeRemaining = inProgress ? end - now + extraTime : null;
  const timeSinceStart = inProgress ? now - new Date(start) : null;
  const timeUntilStart = notYetStarted ? windowStart - now : null;
  const timeUntilEndOfWindow = !hasFinalised ? windowEnd - now : null;

  const data = {
    timeRemaining,
    extraTimeAdjustment,
    timeSinceStart,
    timeUntilStart,
    hasStarted,
    hasFinalised,
    timeUntilEndOfWindow,
  };

  if (Number.isNaN(windowStart) || Number.isNaN(windowEnd)) return;

  updateTimingInfo(node, data);
};

const nodes = [...document.getElementsByClassName('timing-information')];

const refreshAll = () => {
  nodes.forEach((node) => {
    offlineRefresh(node);
  });
};

refreshAll();

export default function initTiming(websocket) {
  websocket.add({
    onError: () => refreshAll(),
    onData: (d) => {
      if (d.type === 'AssessmentTimingInformation') {
        d.assessments.forEach((assessment) => {
          const node = document.querySelector(`.timing-information[data-id="${assessment.id}"]`);
          if (node) updateTimingInfo(node, assessment);
        });
      }
    },
    onHeartbeat: (ws) => {
      const data = nodes.length === 1 ? { assessmentId: nodes[0].getAttribute('data-id') } : null;
      const message = {
        type: 'RequestAssessmentTiming',
        data,
      };

      ws.send(JSON.stringify(message));
    },
  });
  websocket.connect();
}
