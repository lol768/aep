import { calculateTimingInfo, SubmissionState } from 'assessment-timing';

const MINUTE = 60000;

const BASE_TIME = 1555000000000; // Thu Apr 11 2019 17:26:40

const dataDefaults = {
  submissionState: SubmissionState.None
};

describe('calculateTimingInfo', () => {

  it('hides start button before window opens', () => {
    const result = calculateTimingInfo({
      ...dataDefaults,
      windowStart: BASE_TIME + 90*MINUTE,
      windowEnd: BASE_TIME + 360*MINUTE,
      // start: null,
      // end: null,
      // hasStarted: false,
      // hasFinalised: false,
      // extraTimeAdjustment: null,
      // showTimeRemaining: true,
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: 'You can start between 18:56, Thursday 11th April 2019 and 23:26, Thursday 11th April 2019 BST, in 1 hour and 30 minutes unless otherwise advised by your department.',
      allowStart: false,
      hourglassSpins: true
    })
  });

  it('includes recommended start time along with technically possible last start time', () => {
    const result = calculateTimingInfo({
      ...dataDefaults,
      windowStart: BASE_TIME + 90*MINUTE,
      windowEnd: BASE_TIME + 360*MINUTE,
      lastRecommendedStart: BASE_TIME + 105*MINUTE + 90*MINUTE
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: 'You can start between 18:56, Thursday 11th April 2019 and 23:26, Thursday 11th April 2019 BST, in 1 hour and 30 minutes unless otherwise advised by your department.\n\nStart before 20:41, Thursday 11th April 2019 BST to give yourself the full time available.',
      allowStart: false,
      hourglassSpins: true
    })
  });

  it('shows start button inside window', () => {
    const result = calculateTimingInfo({
      ...dataDefaults,
      windowStart: BASE_TIME - 10*MINUTE,
      windowEnd: BASE_TIME + 350*MINUTE,
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: 'This assessment opened at 17:16, Thursday 11th April 2019, and closes 23:16, Thursday 11th April 2019 BST. You have 5 hours and 50 minutes left to start.',
      allowStart: true,
      hourglassSpins: true
    })
  });

  it('prevents start after end of last start time', () => {
    const result = calculateTimingInfo({
      ...dataDefaults,
      windowStart: BASE_TIME - 300*MINUTE,
      windowEnd: BASE_TIME - 10*MINUTE,
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: 'The assessment window has now passed.',
      allowStart: false,
      hourglassSpins: false
    })
  });

  it('shows time remaining', () => {
    const data = {
      ...dataDefaults,
      windowStart: BASE_TIME + 90*MINUTE,
      windowEnd: BASE_TIME + 360*MINUTE,
      start: BASE_TIME - 10*MINUTE,
      end: BASE_TIME + 65*MINUTE,
      hasStarted: true,
      hasFinalised: false,
      extraTimeAdjustment: null,
      showTimeRemaining: true,
    };

    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'You started 10 minutes ago. You have 1 hour and 5 minutes remaining until you should upload your answers.',
      allowStart: false,
      hourglassSpins: true
    });

    // extraTimeAdjustment used only for formatting - it's already included in the end date.
    data.extraTimeAdjustment = 21*MINUTE;
    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'You started 10 minutes ago. You have 1 hour and 5 minutes remaining until you should upload your answers (including 21 minutes additional time).',
      allowStart: false,
      hourglassSpins: true
    });
  });

  it('shows time remaining now is slightly before start', () => {
    const now = 1586461491323;
    const result = calculateTimingInfo({
        ...dataDefaults,
        windowStart : 1586461200000,
        windowEnd : 1586547600000,
        start : 1586461491324,
        end : 1586474991324,
        hasStarted : true,
        hasFinalised : false,
        showTimeRemaining : true,
        progressState : "InProgress"
      }, now);
    expect(result).to.deep.equal({
      warning: false,
      text: 'You started a moment ago. You have 3 hours and 45 minutes remaining until you should upload your answers.',
      allowStart: false,
      hourglassSpins: true
    });
  });

  it('explains when you submitted on-time but currently in the Late period', () => {
    const data = {
      ...dataDefaults,
      start: BASE_TIME - 125*MINUTE,
      end: BASE_TIME - 5*MINUTE,
      hasStarted: true,
      hasFinalised: false,
      extraTimeAdjustment: null,
      showTimeRemaining: true,
      submissionState: SubmissionState.OnTime,
      progressState: 'Late',
    }
    const result = calculateTimingInfo(data, BASE_TIME);
    expect(result).to.deep.equal({
      warning: false,
      text: 'You uploaded your answers on time. If you upload any more answers you may be counted as late.',
      allowStart: false,
      hourglassSpins: true,
    });
  });

  // TODO this (and a bunch of other stuff) will change with OE-279 where finalising becomes optional
  it('explains when you submitted on-time but currently in the DeadlineMissed period', () => {
    const data = {
      ...dataDefaults,
      start: BASE_TIME - 125*MINUTE,
      end: BASE_TIME - 5*MINUTE,
      hasStarted: true,
      hasFinalised: false,
      extraTimeAdjustment: null,
      showTimeRemaining: true,
      submissionState: SubmissionState.OnTime,
      progressState: 'DeadlineMissed',
    }
    const result = calculateTimingInfo(data, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: "You started this assessment, but missed the deadline to finalise your submission.\nExceeded deadline by 5 minutes.",
      allowStart: false,
      hourglassSpins: false
    });
  });

  it('shows a missed deadline', () => {
    const data = {
      ...dataDefaults,
      start: BASE_TIME - 125*MINUTE,
      end: BASE_TIME - 5*MINUTE,
      hasStarted: true,
      hasFinalised: false,
      extraTimeAdjustment: null,
      showTimeRemaining: true,
    };

    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: true,
      text: "You started this assessment, but missed the deadline to upload your answers.\nExceeded deadline by 5 minutes.",
      allowStart: false,
      hourglassSpins: false
    });
  });

  it('shows message when finalised', () => {
    const data = {
      ...dataDefaults,
      hasStarted: true,
      hasFinalised: true,
    };

    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'You completed this assessment.',
      allowStart: false,
      hourglassSpins: false
    });
  });

  it('optionally hides time remaining', () => {
    const data = {
      ...dataDefaults,
      windowStart: BASE_TIME + 90*MINUTE,
      windowEnd: BASE_TIME + 360*MINUTE,
      start: BASE_TIME - 10*MINUTE,
      end: BASE_TIME + 65*MINUTE,
      hasStarted: true,
      hasFinalised: false,
      extraTimeAdjustment: null,
      showTimeRemaining: false,
    };

    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'You started 10 minutes ago.',
      allowStart: false,
      hourglassSpins: true
    });

    // extraTimeAdjustment makes no difference here
    data.extraTimeAdjustment = 21*MINUTE;
    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'You started 10 minutes ago.',
      allowStart: false,
      hourglassSpins: true
    });
  });

  it('does not warn for a "missed" deadline if showTimeRemaining = false', () => {
    const data = {
      ...dataDefaults,
      start: BASE_TIME - 125*MINUTE,
      end: BASE_TIME - 5*MINUTE,
      hasStarted: true,
      hasFinalised: false,
      extraTimeAdjustment: null,
      showTimeRemaining: false,
    };

    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: "You started this assessment, but missed the deadline to upload your answers.",
      allowStart: false,
      hourglassSpins: false
    });
  });

  it('describes fixed-start before start', () => {
    const result = calculateTimingInfo({
      ...dataDefaults,
      durationStyle: 'FixedStart',
      windowStart: BASE_TIME + 90*MINUTE,
      windowEnd: BASE_TIME + 360*MINUTE,
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: 'This assessment will start at 18:56, Thursday 11th April 2019, in 1 hour and 30 minutes.',
      allowStart: false,
      hourglassSpins: true
    })
  });

  it('describes fixed-start after start', () => {
    const result = calculateTimingInfo({
      ...dataDefaults,
      durationStyle: 'FixedStart',
      windowStart: BASE_TIME - 10*MINUTE,
      windowEnd: BASE_TIME + 350*MINUTE,
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: 'This assessment began at 17:16, Thursday 11th April 2019. Start now.',
      allowStart: true,
      hourglassSpins: true
    })
  });

  it('describes fixed-start before end', () => {
    const result = calculateTimingInfo({
      ...dataDefaults,
      durationStyle: 'FixedStart',
      windowStart: BASE_TIME - 300*MINUTE,
      windowEnd: BASE_TIME - 10*MINUTE,
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: 'The assessment has ended.',
      allowStart: false,
      hourglassSpins: false
    })
  });


});
