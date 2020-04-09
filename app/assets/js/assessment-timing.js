import msToHumanReadable from './time-helper';
import JDDT from './jddt';

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
 * @property {boolean} showTimeRemaining - should the time remaining be displayed
 */

/**
* @typedef {Object} TimingResult
* @property {string} text
* @property {boolean} allowStart
* @property {boolean} warning
*/

/** @type {NodeListOf<Element>} */
let nodes;
// Pre-parse data-rendering JSON so it can be easily re-used (or even modified over time)
let nodeData = {};


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

/** Set the list of nodes containing timing information. Generally set by the side-effects
 * at the end of this module but can be reset for testing.
 * @param {NodeListOf<Element>} nodesIn
 */
function setNodes(nodesIn) {
  nodes = nodesIn;
  nodeData = {};
  nodes.forEach((node) => {
    const { dataset: { id, rendering } } = node;
    nodeData[id] = JSON.parse(rendering);
  });
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
    showTimeRemaining,
  } = data;

  const hasWindowPassed = now > windowEnd;
  const inProgress = hasStarted && !hasFinalised;
  const notYetStarted = !hasStarted && !hasWindowPassed;
  const timeRemaining = inProgress ? end - now : null;
  const timeSinceStart = inProgress ? now - new Date(start) : null;
  const timeUntilStart = notYetStarted ? windowStart - now : null;
  const timeUntilEndOfWindow = !hasFinalised ? windowEnd - now : null;

  const jdWindowStart = new JDDT(windowStart);
  const jdWindowEnd = new JDDT(windowEnd);

  let text;
  let warning = false;
  if (hasFinalised) {
    text = 'You completed this assessment.';
  } else if (hasStarted) {
    if (timeRemaining > 0) {
      text = `You started ${msToHumanReadable(timeSinceStart)} ago.`;
      if (showTimeRemaining) {
        text += ` You have ${msToHumanReadable(timeRemaining)} remaining until you should upload your answers`;
        if (extraTimeAdjustment) {
          text += ` (including ${msToHumanReadable(extraTimeAdjustment)} additional time)`;
        }
        text += '.';
      }
    } else {
      text = 'You started this assessment, but missed the deadline to upload your answers.';
      if (showTimeRemaining) {
        text += `\nExceeded deadline by ${msToHumanReadable(-timeRemaining)}.`;
        warning = true;
      }
    }
  } else if (timeUntilStart > 0) {
    text = `You can start between ${jdWindowStart.localString()} and ${jdWindowEnd.localString(true)}, in ${msToHumanReadable(timeUntilStart)}.`;
    warning = true;
  } else if (timeUntilEndOfWindow > 0) {
    text = `This assessment opened at ${jdWindowStart.localString()}, and closes at ${jdWindowEnd.localString()}. You have ${msToHumanReadable(timeUntilEndOfWindow)} left to start it.`;
    warning = true;
  } else {
    text = 'The assessment window has now passed.';
    warning = true;
  }

  return {
    allowStart: !hasStarted && timeUntilStart <= 0 && timeUntilEndOfWindow > 0,
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

export function receiveSocketData(d) {
  if (d.type === 'AssessmentTimingInformation') {
    const { now, assessments } = d;
    assessments.forEach((assessment) => {
      const { id } = assessment;
      let node = document.querySelector(`.timing-information[data-id="${id}"]`);
      if (node) {
        const data = nodeData[id];
        // partial update of properties
        nodeData[id] = {
          ...data,
          ...assessment,
        };
        domRefresh(node, now);
      }

      node = document.querySelector(`.timeline[data-id="${id}"]`);
      if (node) {
        const { progressState } = assessment;
        if (progressState) {
          node.querySelectorAll('.block').forEach((e) => e.classList.remove('active'));
          node.querySelectorAll(`.block[data-state="${progressState}"`).forEach((e) => e.classList.add('active'));
        }
      }
    });
  }
}

export default function initTiming(websocket) {
  websocket.add({
    onError: () => {
      localRefreshAll();
    },
    onData: receiveSocketData,
    onHeartbeat: (ws) => {
      const data = nodes.length === 1 ? { assessmentId: nodes[0].getAttribute('data-id') } : undefined;
      const message = {
        type: 'RequestAssessmentTiming',
        data,
      };

      ws.send(JSON.stringify(message));
    },
  });
  websocket.connect();
}

// side-effects
setNodes(document.querySelectorAll('.timing-information'));
localRefreshAll();
