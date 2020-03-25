import chai from "chai";
import JDDT from "jddt";

describe('JDDT date formatting', () => {

  it('provide formatted localised date string based on provided milliseconds', () => {
    // 09:30, 25/03/2020
    const millis = 1585128600000;
    const jddt = new JDDT(millis);
    assert(jddt.longLocal() === "hog")
  });

});
