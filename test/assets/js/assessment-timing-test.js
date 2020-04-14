import { calculateTimingInfo } from 'assessment-timing';

const MINUTE = 60000;

const BASE_TIME = 1555000000000; // Thu Apr 11 2019 17:26:40

describe('calculateTimingInfo', () => {

  it('hides start button before window opens', () => {
    const result = calculateTimingInfo({
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
      allowStart: false,
      hourglassSpins: true
    })
  });

  it('shows start button inside window', () => {
    const result = calculateTimingInfo({
      windowStart: BASE_TIME - 10*MINUTE,
      windowEnd: BASE_TIME + 350*MINUTE,
    }, BASE_TIME);
    expect(result).to.deep.equal({
      warning: true,
      text: '5 hours and 50 minutes left to start.',
      allowStart: true,
      hourglassSpins: true
    })
  });

  it('prevents start after end of last start time', () => {
    const result = calculateTimingInfo({
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
      allowStart: false,
      hourglassSpins: true
    });

    // extraTimeAdjustment used only for formatting - it's already included in the end date.
    data.extraTimeAdjustment = 21*MINUTE;
    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'Started 10 minutes ago. 1 hour and 5 minutes remaining (including 21 minutes additional time).',
      allowStart: false,
      hourglassSpins: true
    });
  });

  it('shows time remaining now is slightly before start', () => {
    const now = 1586461491323;
    const result = calculateTimingInfo({
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

  it('shows a missed deadline', () => {
    const data = {
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
      allowStart: false,
      hourglassSpins: true
    });

    // extraTimeAdjustment makes no difference here
    data.extraTimeAdjustment = 21*MINUTE;
    expect(calculateTimingInfo(data, BASE_TIME)).to.deep.equal({
      warning: false,
      text: 'Started 10 minutes ago.',
      allowStart: false,
      hourglassSpins: true
    });
  });

  it('does not warn for a "missed" deadline if showTimeRemaining = false', () => {
    const data = {
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

});
