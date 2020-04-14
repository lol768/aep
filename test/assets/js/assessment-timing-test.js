import { calculateTimingInfo, SubmissionState } from 'assessment-timing';

const MINUTE = 60000;

const BASE_TIME = 1555000000000; // Thu Apr 11 2019 17:26:40

const dataDefaults = {
  submissionState: SubmissionState.None
}

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
      text: 'You can start in 1 hour and 30 minutes.',
      allowStart: false
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
      text: '5 hours and 50 minutes left to start.',
      allowStart: true
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
      allowStart: false
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
      text: 'Started 10 minutes ago. 1 hour and 5 minutes remaining.',
      allowStart: false
    });

    // extraTimeAdjustment used only for formatting - it's already included in the end date.
    data.extraTimeAdjustment = 21*MINUTE;
    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'Started 10 minutes ago. 1 hour and 5 minutes remaining (including 21 minutes additional time).',
      allowStart: false
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
      text: 'Started a moment ago. 3 hours and 45 minutes remaining.',
      allowStart: false
    });
  });

  it('explains when you submitted on-time but currently in the late period', () => {
    const data = {
      ...dataDefaults,
      start: BASE_TIME - 125*MINUTE,
      end: BASE_TIME - 5*MINUTE,
      hasStarted: true,
      hasFinalised: false,
      extraTimeAdjustment: null,
      showTimeRemaining: true,
      submissionState: SubmissionState.OnTime,
    }


  })

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
      text: "You have provided answers on time. If you add more files you may be considered late.",
      allowStart: false
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
      allowStart: false
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
      text: 'Started 10 minutes ago.',
      allowStart: false
    });

    // extraTimeAdjustment makes no difference here
    data.extraTimeAdjustment = 21*MINUTE;
    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'Started 10 minutes ago.',
      allowStart: false
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
      allowStart: false
    });
  });

});
