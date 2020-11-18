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
 * @property {unix_timestamp} lastRecommendedStart - last start time to enjoy full duration
 * @property {unix_timestamp} start - when exam was started (if it has started)
 * @property {unix_timestamp} end - latest time to submit without being late
 *           (start + duration + user's reasonable adjustment)
 * @property {boolean} hasStarted - has the exam started
 * @property {boolean} hasFinalised - have answers been submitted and finalised
 * @property {millis} extraTimeAdjustment - any reasonable adjustment the user has
 * @property {boolean} showTimeRemaining - should the time remaining be displayed
 * @property {string} progressState - the ProgressState value
 * @property {string} submissionState - the SubmissionState value
 * @property {string} durationStyle - the DurationStyle value
 */

/**
* @typedef {Object} TimingResult
* @property {string} text
* @property {boolean} allowStart
* @property {boolean} warning
*/

/** @type {NodeListOf<Element>} */
let nodes;
// Pre-parse data-rendering JSON so it can be easily re-used and modified over time
let nodeData = {};

export const SubmissionState = {
  None: 'None',
  Submitted: 'Submitted',
  OnTime: 'OnTime',
  Late: 'Late',
};

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

function stopHourglassSpinning({ parentElement }) {
  const spinner = parentElement.querySelector('i.fa-hourglass-spin');
  if (!spinner) return;
  spinner.classList.remove('fa-hourglass-spin');
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
    lastRecommendedStart,
    start,
    end,
    hasStarted,
    hasFinalised,
    extraTimeAdjustment,
    showTimeRemaining,
    progressState,
    submissionState,
    durationStyle,
  } = data;

  const hasWindowPassed = now > windowEnd;
  const inProgress = hasStarted && !hasFinalised;
  const notYetStarted = !hasStarted && !hasWindowPassed;
  const timeRemaining = inProgress ? end - now : null;
  const timeSinceStart = inProgress ? Math.max(0, now - start) : null;
  const timeUntilStart = notYetStarted ? windowStart - now : null;
  const timeUntilEndOfWindow = !hasFinalised ? windowEnd - now : null;
  const timeUntilLastRecommendedStart = (!inProgress && !hasFinalised)
    ? lastRecommendedStart - now : null;

  let text;
  let warning = false;
  let hourglassSpins = false;
  if (hasFinalised) {
    text = 'You completed this assessment.';
  } else if (hasStarted) {
    if (timeRemaining > 0) {
      hourglassSpins = true;
      text = `You started ${msToHumanReadable(timeSinceStart)} ago.`;
      if (showTimeRemaining) {
        text += ` You have ${msToHumanReadable(timeRemaining)} remaining until you should upload your answers`;
        if (extraTimeAdjustment) {
          text += ` (including ${msToHumanReadable(extraTimeAdjustment)} additional time)`;
        }
        text += '.';
      }
    } else if (submissionState === SubmissionState.OnTime && progressState === 'Late') {
      text = 'You uploaded your answers on time. If you upload any more answers you may be counted as late.';
      hourglassSpins = true;
    } else if (timeRemaining === null) {
      // This is technically possible if durationStyle is undefined...
      text = `This assessment opened at ${new JDDT(windowStart).localString(false)}`;
    } else {
      // In practice I don't think we will ever print the "finalise your submission" version any
      // more, because if you submitted anything and the time ran out, it's considered finalised
      // and would be handled at the very top
      const action = submissionState === SubmissionState.None ? 'upload your answers' : 'finalise your submission';
      text = `You started this assessment, but missed the deadline to ${action}.`;
      if (showTimeRemaining) {
        text += `\nExceeded deadline by ${msToHumanReadable(-timeRemaining)}.`;
        warning = true;
      }
    }
  } else {
    switch (durationStyle) {
      case 'FixedStart':
        if (timeUntilStart > 0) {
          text = `This assessment will start at ${new JDDT(windowStart).localString(true)}, in ${msToHumanReadable(timeUntilStart)}.`;
          warning = true;
          hourglassSpins = true;
        } else if (timeUntilEndOfWindow > 0) {
          text = `This assessment began at ${new JDDT(windowStart).localString(true)}. Start now.`;
          hourglassSpins = true;
          warning = true;
        } else {
          text = 'The assessment has ended.';
          warning = true;
        }
        break;
      case 'DayWindow':
        if (timeUntilStart > 0) {
          text = `You can start between ${new JDDT(windowStart).localString(false)} and ${new JDDT(windowEnd).localString(true)}, in ${msToHumanReadable(timeUntilStart)} unless otherwise advised by your department.`;
          warning = true;
          hourglassSpins = true;
          if (lastRecommendedStart) {
            text += `\n\nStart no later than ${new JDDT(lastRecommendedStart).localString(true)} to give yourself the full time available.`;
          }
        } else if (timeUntilEndOfWindow > 0) {
          text = `This assessment opened at ${new JDDT(windowStart).localString(false)}, and closes ${new JDDT(windowEnd).localString(true)}. You have ${msToHumanReadable(timeUntilEndOfWindow)} left to start.`;
          if (timeUntilLastRecommendedStart > 0) {
            text += ` To give yourself the full time available, you should start in the next ${msToHumanReadable(timeUntilLastRecommendedStart)}.`;
          }
          hourglassSpins = true;
          warning = true;
        } else {
          text = 'The assessment window has now passed.';
          warning = true;
        }
        break;
      default:
        // undefined durationStyle is a valid thing
        if (timeUntilStart > 0) {
          text = `This assessment will start at ${new JDDT(windowStart).localString(true)}, in ${msToHumanReadable(timeUntilStart)}.`;
          warning = true;
          hourglassSpins = true;
        } else {
          // We should never get here, but just in case...
          text = `This assessment opened at ${new JDDT(windowStart).localString(false)}`;
        }
        break;
    }
  }

  return {
    allowStart: !hasStarted && timeUntilStart <= 0 && timeUntilEndOfWindow > 0,
    hourglassSpins,
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

  const {
    text, warning, allowStart, hourglassSpins,
  } = calculateTimingInfo(data, now);

  markParentForm(node, allowStart, hourglassSpins);

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

  if (!hourglassSpins) {
    stopHourglassSpinning(node);
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

function updateTimeline(timelineNode, id, assessment) {
  if (timelineNode) {
    const { progressState } = assessment;
    if (progressState) {
      timelineNode.querySelectorAll('.block').forEach((e) => e.classList.remove('active'));
      timelineNode.querySelectorAll(`.block[data-state="${progressState}"]`).forEach((e) => e.classList.add('active'));
    }
  }
}

function showLateWarning() {
  const lateWarningNode = document.querySelector('#late-upload-warning');
  if (lateWarningNode) {
    lateWarningNode.innerHTML = `
                    <div class="alert alert-warning media">
                      <div class="media-left">
                        <i class="fas fa-exclamation-triangle"></i>
                      </div>
                      <div class="media-body">
                        If you upload new files at this point your submission may be considered as late.
                      </div>
                    </div>`;
  }
}

function showDeadlineMissed(timelineNode) {
  if (timelineNode) {
    const contactInvigilatorLink = document.getElementById('contactInvigilatorLink');
    const fileInputs = document.querySelectorAll('input[type=file]');
    const deleteButtons = document.querySelectorAll('button[delete]');
    const uploadFilesButton = document.getElementById('uploadFilesButton');
    const agreeDisclaimerCheckbox = document.getElementById('agreeDisclaimer');
    const finishAssessmentButton = document.getElementById('finishAssessmentButton');

    if (contactInvigilatorLink) {
      const span = document.createElement('span');
      span.classList.add('text-muted');
      span.textContent = contactInvigilatorLink.textContent;
      contactInvigilatorLink.replaceWith(span);
    }
    // eslint-disable-next-line no-param-reassign
    fileInputs.forEach((input) => { input.disabled = true; });
    // eslint-disable-next-line no-param-reassign
    deleteButtons.forEach((button) => { button.disabled = true; });
    if (uploadFilesButton) {
      uploadFilesButton.disabled = true;
    }
    if (agreeDisclaimerCheckbox) {
      agreeDisclaimerCheckbox.disabled = true;
    }
    if (finishAssessmentButton) {
      finishAssessmentButton.disabled = true;
    }
  }
}

export function receiveSocketData(d) {
  if (d.type === 'AssessmentTimingInformation') {
    const { now, assessments } = d;
    assessments.forEach((assessment) => {
      const { id } = assessment;
      const node = document.querySelector(`.timing-information[data-id="${id}"]`);
      if (node) {
        const data = nodeData[id];
        // partial update of properties
        // TODO as of OE-253 the socket sends all data so no point doing a partial update here.
        nodeData[id] = {
          ...data,
          ...assessment,
        };
        domRefresh(node, now);
      }

      const timelineNode = document.querySelector(`.timeline[data-id="${id}"]`);
      updateTimeline(timelineNode, id, assessment);

      if (assessment.progressState === SubmissionState.Late) {
        showLateWarning();
      }

      if (assessment.progressState === 'DeadlineMissed') {
        showDeadlineMissed(timelineNode);
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
}

// side-effects
setNodes(document.querySelectorAll('.timing-information'));
localRefreshAll();
