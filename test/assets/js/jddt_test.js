import JDDT from "jddt";

const iconString = '<i class="fad fa-clock fa-fw" aria-hidden="true"></i>';
const londonTimeString = '<span class="text-muted">Europe/London</span>';
const currentYear = new Date().getFullYear();

describe('JDDT date formatting', () => {

  it('provide formatted localised date string based on provided milliseconds', () => {
    // 13:30, 25/03/2020 (GMT)
    const millis = 1585056600000;
    const jddt = new JDDT(millis);
    // Future proofing in case we run this test in the  f u t u r e
    // - year doesn't show if it's the current year
    const yearString = new Date(millis).getFullYear() === currentYear
      ? ''
      : '2020';
    assert.equal(jddt.longLocal(), `${iconString} Tuesday 24th March${yearString}, 13:30 ${londonTimeString}`);
  });

});
