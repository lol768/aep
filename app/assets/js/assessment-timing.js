import msToHumanReadable from './time-helper';

/**
 * @typedef {number} unix_timestamp
 * @typedef {number} millis
 */

/**
 * @typedef {Object} TimingData
 * @property {unix_timestamp} windowStart - earliest allowed start time
 * @property {unix_timestamp} windowEnd - latest allowed start time
 * @property {unix_timestamp} start - when exam was started (if it has started)
 * @property {unix_timestamp} end - latest time to submit without being late
 *           (start + duration + user's reasonable adjustment)
 * @property {boolean} hasStarted - has the exam started
 * @property {boolean} hasFinalised - have answers been submitted and finalised
 * @property {millis} extraTimeAdjustment - any reasonable adjustment the user has
 */

/**
* @typedef {Object} TimingResult
* @property {string} text
* @property {boolean} allowStart
* @property {boolean} warning
*/


const nodes = document.querySelectorAll('.timing-information');

// Pre-parse data-rendering JSON so it can be easily re-used (or even modified over time)
const nodeData = {};
nodes.forEach((node) => {
  const { dataset: { id, rendering } } = node;
  nodeData[id] = JSON.parse(rendering);
});

/** */
function clearWarning({ parentElement }) {
  parentElement.classList.remove('text-danger');
  parentElement.classList.add('text-info');
}

/** */
function setWarning({ parentElement }) {
  parentElement.classList.add('text-danger');
  parentElement.classList.remove('text-info');
}

/**
 * @param {Element} node
 * @param {boolean} allowStart
 */
function markParentForm(node, allowStart) {
  const form = node.closest('form');
  if (!form) return;
  const submitBtn = form.querySelector('.btn[type=submit]');
  if (!submitBtn) return;
  if (allowStart) {
    submitBtn.classList.remove('hide');
  } else {
    submitBtn.classList.add('hide');
  }
}

/**
 * A pure function that takes the data and time (whether it's come from the DOM or a WS)

 * @param {TimingData} data
 * @param {unix_timestamp} now
 * @returns {TimingResult} a result that can be used to update the page.
 */
export function calculateTimingInfo(data, now) {
  const {
    windowStart,
    windowEnd,
    start,
    end,
    hasStarted,
    hasFinalised,
    extraTimeAdjustment,
  } = data;

  const hasWindowPassed = now > windowEnd;
  const inProgress = hasStarted && !hasFinalised;
  const notYetStarted = !hasStarted && !hasWindowPassed;
  const timeRemaining = inProgress ? end - now : null;
  const timeSinceStart = inProgress ? now - new Date(start) : null;
  const timeUntilStart = notYetStarted ? windowStart - now : null;
  const timeUntilEndOfWindow = !hasFinalised ? windowEnd - now : null;

  let text;
  let warning = false;
  if (hasFinalised) {
    text = 'Assessment complete.';
  } else if (hasStarted) {
    text = `Started ${msToHumanReadable(timeSinceStart)} ago.`;
    if (timeRemaining > 0) {
      text += ` ${msToHumanReadable(timeRemaining)} remaining`;
      if (extraTimeAdjustment) {
        text += ` (including ${msToHumanReadable(extraTimeAdjustment)} additional time)`;
      }
      text += '.';
    } else {
      text += `\nExceeded deadline by ${msToHumanReadable(-timeRemaining)}.`;
      warning = true;
    }
  } else if (timeUntilStart > 0) {
    text = `You can start in ${msToHumanReadable(timeUntilStart)}.`;
    warning = true;
  } else if (timeUntilEndOfWindow > 0) {
    text = `${msToHumanReadable(timeUntilEndOfWindow)} left to start.`;
    warning = true;
  } else {
    text = 'The assessment window has now passed.';
    warning = true;
  }

  return {
    allowStart: !hasStarted && timeUntilStart <= 0 /* && timeUntilEndOfWindow > 0 */,
    text,
    warning,
  };
}

/**
 * @param {Element} node
 * @param {TimingData} data
 * @param {unix_timestamp} now
 * @return {void}
 */
function updateTimingInfo(node, data, now) {
  if (Number.isNaN(data.windowStart) || Number.isNaN(data.windowEnd)) return;

  const { text, warning, allowStart } = calculateTimingInfo(data, now);

  markParentForm(node, allowStart);

  const textNode = document.createTextNode(text);
  const existingTextNode = node.lastChild;
  if (existingTextNode) {
    node.replaceChild(textNode, existingTextNode);
  } else {
    node.appendChild(textNode);
  }

  if (warning) {
    setWarning(node);
  } else {
    clearWarning(node);
  }
}

/**
 * @param {Element} node
 * @param {unix_timestamp} now
 * @return {void}
 */
function domRefresh(node, now) {
  const { dataset: { id } } = node;
  const data = nodeData[id];
  updateTimingInfo(node, data, now);
}

/**
 * Refresh all nodes using local clock.
 */
function localRefreshAll() {
  const now = Number(new Date());
  nodes.forEach((node) => {
    domRefresh(node, now);
  });
}

localRefreshAll();

export default function initTiming(websocket) {
  websocket.add({
    onError: () => {
      localRefreshAll();
    },
    onData: (d) => {
      if (d.type === 'AssessmentTimingInformation') {
        // Just sending timestamp, using what we have in dataset for the rest.
        // TODO might want to send over start, end, and hasStarted
        // so it can update assessments that start themselves at a specific time.
        const { now } = d;
        nodes.forEach((node) => {
          domRefresh(node, now);
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
