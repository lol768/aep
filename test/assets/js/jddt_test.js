import JDDT from 'jddt';
import {JSDOM} from 'jsdom';
import MockDate from 'mockdate';
import moment from 'moment-timezone';

const iconString = '<i class="fad themed-duotone fa-clock fa-fw" aria-hidden="true"></i>';
const gmtTimeString = '<span class="text-muted">GMT</span>';
const bstTimeString = '<span class="text-muted">BST</span>';
const moscowTimeString = '<span class="text-muted">MSK</span>';
const midwayTimeString = '<span class="text-muted">SST</span>';
const dom = new JSDOM('<!DOCTYPE html><html><head></head><body></body></html>');
const document = dom.window.document;

// These tests will work on this assumption that today's date is as specified below:
// 2020-03-25T09:45:00.000Z
process.env.TZ = 'Europe/London';
moment.tz.guess(true);
const fakeNow = 1585129500000;
MockDate.set(fakeNow, 0);

// Quick element maker for individual datetime
function makeJDDTElement(millis, format, serverTimezoneOffset, serverTimezoneName) {
  const el = document.createElement('span');
  el.setAttribute('data-millis', millis);
  el.setAttribute('data-format', format);
  el.setAttribute('data-server-timezone-offset', serverTimezoneOffset);
  el.setAttribute('data-server-timezone-name', serverTimezoneName);
  return el;
}

// Quick element maker for datetime range
function makeJDDTRangeElement(short, fromMillis, toMillis, serverTimezoneOffset, serverTimezoneName) {
  const el = document.createElement('span');
  el.setAttribute('data-short', short);
  el.setAttribute('data-from-millis', fromMillis);
  el.setAttribute('data-to-millis', toMillis);
  el.setAttribute('data-server-timezone-offset', serverTimezoneOffset);
  el.setAttribute('data-server-timezone-name', serverTimezoneName);
  return el;
}

describe('JDDT date formatting', () => {

  // Assumes the machine running the test is in Europe/London timezone
  it('provides formatted localised date string based on provided milliseconds', () => {
    // 10:30, 24/03/2020 (GMT)
    const millis = 1585045800000;
    const jddt = new JDDT(millis);

    assert.equal(jddt.longLocal(), `${iconString} 10:30, Tuesday 24th March ${gmtTimeString}`);
    assert.equal(jddt.shortLocal(), `${iconString} 10:30, Tue 24th Mar ${gmtTimeString}`);
  });

  it('identifies if the milliseconds provided refer to today\'s date', () => {
    const jddt = new JDDT(Date.now());

    assert.match(jddt.longLocal(), /today/);
    assert.match(jddt.shortLocal(), /today/);
  });

  it('handles different timezones properly', () => {
    // 10:30, 24/03/2020 (GMT) - as tested against the standard London time above
    const millis = 1585045800000;
    const jddt = new JDDT(millis);

    jddt.setLocalTimezone(180, "Europe/Moscow");

    assert.equal(jddt.longLocal(), `${iconString} 13:30, Tuesday 24th March ${moscowTimeString}`);
    assert.equal(jddt.shortLocal(), `${iconString} 13:30, Tue 24th Mar ${moscowTimeString}`);

    jddt.setLocalTimezone(-660, 'Pacific/Midway');
    assert.equal(jddt.longLocal(), `${iconString} 23:30, Monday 23rd March ${midwayTimeString}`);
    assert.equal(jddt.shortLocal(), `${iconString} 23:30, Mon 23rd Mar ${midwayTimeString}`);
  });

  it('shows the year if it\'s not the current year', () => {
    // 10:30, 24/03/2019 (GMT)
    const millis = 1553423400000;
    const jddt = new JDDT(millis);

    assert.match(jddt.longLocal(), /2019/);
    assert.match(jddt.shortLocal(), /'19/);
  });

  it('doesn\'t populate an element with text if supplied server timezone matches local timezone', () => {
    const el = makeJDDTElement(1585045800000, 'shortLocal', 0, 'Europe/London');
    JDDT.applyToElement(el);

    assert.equal(el.innerHTML, '');
  });

  it('applies to an element if supplied server timezone is different to local timezone', () => {
    const millis = 1585045800000;
    const el = makeJDDTElement(millis, 'shortLocal', 180, 'Europe/Moscow');
    JDDT.applyToElement(el);

    // Assuming the web browser is on UK time...
    assert.equal(el.innerHTML, `${iconString} 10:30, Tue 24th Mar ${gmtTimeString}`);
  });

  it('doesn\'t apply to a range element if supplied server timezone is the same as local timezone', () => {
    const fromMillis = 1585045800000;
    const toMillis = 1585049400000;
    const el = makeJDDTRangeElement(true, fromMillis, toMillis, 0, 'Europe/London');
    JDDT.applyToRangeElement(el);

    assert.equal(el.innerHTML, '');
  });

  it('applies to a range element if supplied server timezone is not the same as local timezone', () => {
    const fromMillis = 1585045800000;
    const toMillis = 1585049400000;
    const el = makeJDDTRangeElement(true, fromMillis, toMillis, 180, 'Europe/Moscow');
    JDDT.applyToRangeElement(el);

    assert.equal(el.innerHTML, `${iconString} 10:30 to 11:30, Tue 24th Mar ${gmtTimeString}`);
  });

  it('identifies today\'s date as Today in date ranges', () => {
    // 10:30, 25/03/2020
    const fromMillis = 1585132200000;
    // 11:30, 25/03/2020
    const toMillis = 1585135800000;
    const el = makeJDDTRangeElement(true, fromMillis, toMillis, 180, 'Europe/Moscow');
    JDDT.applyToRangeElement(el);

    assert.equal(el.innerHTML, `${iconString} 10:30 to 11:30 today ${gmtTimeString}`);
  });

  it ('shows the from day & date if different to the to date in ranges', () => {
    // 11:30, 24/03/2020
    const fromMillis = 1585049400000;
    // 11:30, 26/03/2020
    const toMillis = 1585222200000;
    const el = makeJDDTRangeElement(true, fromMillis, toMillis, 180, 'Europe/Moscow');
    JDDT.applyToRangeElement(el);

    assert.equal(el.innerHTML, `${iconString} 11:30, Tue 24th to 11:30, Thu 26th Mar ${gmtTimeString}`);
  });

  it('shows the from day & date if different to the to date in ranges, taking into account if to date is today', () => {
    // 11:30, 24/03/2020
    const fromMillis = 1585049400000;
    // 11:30, 25/03/2020
    const toMillis = 1585135800000;
    const el = makeJDDTRangeElement(true, fromMillis, toMillis, 180, 'Europe/Moscow');
    JDDT.applyToRangeElement(el);

    assert.equal(el.innerHTML, `${iconString} 11:30, Tue 24th Mar to 11:30 today ${gmtTimeString}`);
  });

  it('shows the from month, day & date if different to the to date in ranges', () => {
    // 11:30, 24/03/2020
    const fromMillis = 1585049400000;
    // 11:30, 02/04/2020
    const toMillis = 1585823400000;
    const el = makeJDDTRangeElement(true, fromMillis, toMillis, 180, 'Europe/Moscow');
    JDDT.applyToRangeElement(el);

    assert.equal(el.innerHTML, `${iconString} 11:30, Tue 24th Mar ${gmtTimeString} to 11:30, Thu 2nd Apr ${bstTimeString}`);
  });

  it('shows the from year, month, day & date if different to the to date in ranges', () => {
    // 11:30, 24/03/2020
    const fromMillis = 1585049400000;
    // 11:30, 24/03/2021
    const toMillis = 1617359400000;
    const el = makeJDDTRangeElement(true, fromMillis, toMillis, 180, 'Europe/Moscow');
    JDDT.applyToRangeElement(el);

    assert.equal(el.innerHTML, `${iconString} 11:30, Tue 24th Mar '20 ${gmtTimeString} to 11:30, Fri 2nd Apr '21 ${bstTimeString}`);
  });

  it('handles ranges across date boundaries', () => {
    // 14:00, 26/03/2020
    const fromMillis = 1585231200000;
    // 22:00, 26/03/2020
    const toMillis = 1585260000000;

    // Change the client timezone offset to match Asia/Shanghai
    try {
      process.env.TZ = 'Asia/Shanghai';
      moment.tz.guess(true);

      // 16:30, 26/03/2020 (00:30, 27/03/2020 local)
      MockDate.set(1585240200000, -480);

      const el = makeJDDTRangeElement(true, fromMillis, toMillis, 0, 'Europe/London');
      JDDT.applyToRangeElement(el);

      assert.equal(el.innerHTML, `${iconString} 22:00, Thu 26th Mar to 06:00 today <span class="text-muted">CST</span>`);
    } finally {
      process.env.TZ = 'Europe/London';
      moment.tz.guess(true);
      MockDate.set(fakeNow, 0);
    }
  });

});
