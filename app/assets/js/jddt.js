// John Dale Datetime (JDDT)

const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const TODAY = 'Today ';
const SOME_SUNNY_DAY = '';
const iconString = '<i class="fad fa-clock fa-fw" aria-hidden="true"></i>';

/**
 * Returns number n as string with extra 0 prepended if it's only 1 digit
 * @private
 * @param {number} n
 * @returns {string}
 */
function pad0(n) {
  return n < 10 ? `0${n}` : n.toString();
}

/**
 * Returns number with "st", "nd", "rd" or "th" as appropriate
 * @private
 * @param {number} n
 * @returns {string}
 */
function th(n) {
  if (n > 10 && n < 21) {
    return `${n}th`;
  }
  const lastDigit = n % 10;
  if (lastDigit === 1) {
    return `${n}st`;
  }
  if (lastDigit === 2) {
    return `${n}nd`;
  }
  if (lastDigit === 3) {
    return `${n}rd`;
  }
  return `${n}th`;
}

/**
 * Copy a date, preserving anything generated by timezone offset
 * @private
 * @param {Date} d
 * @returns {Date}
 */
function cloneDate(d) {
  return new Date(
    d.getFullYear(),
    d.getMonth(),
    d.getDate(),
    d.getHours(),
    d.getMinutes(),
    d.getSeconds(),
    d.getMilliseconds(),
  );
}

/**
 * Do the two dates provided match in terms of year, month and date?
 * @private
 * @param {Date} date1
 * @param {Date} date2
 * @returns {boolean}
 */
function datesMatch(date1, date2) {
  return (date1.getDate() === date2.getDate()
  && date1.getMonth() === date2.getMonth()
  && date1.getFullYear() === date2.getFullYear());
}

/**
 * Returns one of the variables named as YESTERDAY or SOME_SUNNY_DAY above
 * @private
 * @param {Date} date
 * @returns {string} - "today" or ""
 */
function relativeDateName(date) {
  const now = new Date(Date.now());
  const d = cloneDate(date);

  if (datesMatch(now, d)) {
    return TODAY;
  }

  return SOME_SUNNY_DAY;
}

let browserLocalTimezoneName;
if (window.Intl !== undefined && Intl.DateTimeFormat !== undefined) {
  const dtf = Intl.DateTimeFormat();
  if (dtf.resolvedOptions !== undefined && dtf.resolvedOptions().timeZone !== undefined) {
    browserLocalTimezoneName = dtf.resolvedOptions().timeZone;
  }
}

/**
 * Returns a formatted string of a full year
 * @private
 * @function
 * @param {number} year - should be full, 4-digit year (e.g. 2020)
 * @param {boolean} [short] - if true, returned with 2-digit abbreviation (e.g. '20)
 * @returns {string}
 */
function stringifyYear(year, short) {
  return `${short ? `'${year.toString().substring(2, 4)}` : year.toString()}`;
}

/**
 * Returns a formatted string of the supplied month
 * @param {number} month - month as generated by Date.getMonth()
 * @param {boolean} short - if true, returned with 3-character abbreviation (e.g. Mar)
 * @returns {string}
 */
function stringifyMonth(month, short) {
  return `${short ? months[month].substring(0, 3) : months[month]}`;
}

/**
 * Returns the weekday name for the supplied weekday number
 * @param {number} weekdayNumber - as generated by Date.getDay()
 * @param {boolean} [short] - if true, returned with 3-character abbreviation (e.g. Tue)
 * @returns {string}
 */
function stringifyDay(weekdayNumber, short) {
  return `${short ? days[weekdayNumber].substring(0, 3) : days[weekdayNumber]}`;
}

/**
 * Returns a 24 hour formatted time string - e.g. "17:30"
 * @param {Date} date
 * @returns {string}
 */
function stringifyTime(date) {
  return `${pad0(date.getHours())}:${pad0(date.getMinutes())}`;
}

/**
 * Generates long/short JDDT format with timezoneName appended
 * @private
 * @function
 * @param {Date} date - js Date object to format
 * @param {string} timezoneName - name of timezone to be appended
 * @param {boolean} [short] - if this is true the function will return truncated
 * months, days and years
 * @returns {string}
 */
function stringify(date, timezoneName, short) {
  const currentYear = new Date(Date.now()).getFullYear();
  let dateName = relativeDateName(date);
  if (dateName === '') {
    let yearString = '';
    if (date.getFullYear() !== currentYear) {
      yearString = stringifyYear(date.getFullYear());
    }
    dateName = `${stringifyDay(date.getDay(), short)} ${th(date.getDate())} ${stringifyMonth(date.getMonth(), short)} ${yearString}`;
  }
  const dateTimeString = `${dateName.trim()}, ${stringifyTime(date).trim()}`;
  return `${iconString} ${dateTimeString.trim()} <span class="text-muted">${timezoneName}</span>`;
}

/**
 * Takes two dates and returns a stringified range that doesn't duplicate any more
 * info than it needs to
 * @private
 * @static
 * @param {Date} fromDate - range starts from this js Date object
 * @param {Date} toDate - range ends at this js Date object
 * @param {string} timezoneName - name of timezone to be appended
 * @param {boolean} [short] - if this is true the function will return truncated
 * months, days and years
 * @returns {string}
 */
function stringifyDateRange(fromDate, toDate, timezoneName, short) {
  const currentYear = new Date(Date.now()).getFullYear();
  const fromYear = fromDate.getFullYear();
  const toYear = toDate.getFullYear();
  const fromMonth = fromDate.getMonth();
  const toMonth = toDate.getMonth();
  const fromDateNumber = fromDate.getDate();
  const toDateNumber = toDate.getDate();

  const sameYear = fromYear === toYear;
  const sameMonth = fromMonth === toMonth;
  const sameDateNumber = fromDateNumber === toDateNumber;
  const sameExactDate = sameYear && sameMonth && sameDateNumber;

  let fromDateNumberString = '';
  let toDateNumberString = relativeDateName(toDate);

  let fromYearString = '';
  let toYearString = '';

  if (!sameYear || fromYear !== currentYear || toYear !== currentYear) {
    fromYearString = `${stringifyYear(fromYear, short)} `;
    toYearString = `${stringifyYear(toYear, short)} `;
  }

  let fromMonthString = '';
  const toMonthString = toDateNumberString !== TODAY
    ? `${stringifyMonth(toMonth, short)} `
    : '';

  if (!sameYear || !sameMonth || (toDateNumberString === TODAY && !sameExactDate)) {
    fromMonthString = `${stringifyMonth(fromMonth, short)} `;
  }

  if (toDateNumberString === '') {
    toDateNumberString = `${th(toDateNumber)} `;
  }
  let fromDateDayString = '';
  const toDateDayString = toDateNumberString !== TODAY
    ? `${stringifyDay(toDate.getDay(), short)} `
    : '';

  if (!sameYear || !sameMonth || !sameDateNumber) {
    fromDateNumberString = `${th(fromDateNumber)} `;
    fromDateDayString = `${stringifyDay(fromDate.getDay(), short)} `;
  }

  return `<i class="fad fa-clock fa-fw" aria-hidden="true"></i> \
${stringifyTime(fromDate)} \
${fromDateDayString}${fromDateNumberString}${fromMonthString}${fromYearString}\
to \
${stringifyTime(toDate)}, \
${toDateDayString}${toDateNumberString}${toMonthString}${toYearString}\
<span class="text-muted">${timezoneName}</span>`;
}

/**
 * John Dale Datetime object. Handles converts dates to local, JD-style format.
 */
export default class JDDT {
  /**
   * John Dale Datetime object constructor. Works with anything accepted by a plain JS Date object.
   * @constructor
   * @param {Date|number|string} input - accepts a Date, a number of epoch milliseconds
   * or a suitably formatted string
   */
  constructor(input) {
    if (input instanceof Date) {
      this.jsDateLocal = input;
    } else {
      this.jsDateLocal = new Date(input);
    }

    // By default we get the local timezone details from the browser,
    // but these can be overridden for testing using setLocalTimezone

    this.localTimezoneOffset = this.jsDateLocal.getTimezoneOffset();
    if (browserLocalTimezoneName === undefined) {
      this.localTimezoneName = `${this.localTimezoneOffset < 0 ? '-' : '+'}\
        ${pad0(Math.floor(Math.abs(this.localTimezoneOffset / 60)))}:\
        ${pad0(Math.abs(this.localTimezoneOffset) % 60)}`;
    } else {
      this.localTimezoneName = browserLocalTimezoneName;
    }

    // These are the defaults, but should be overwritten using setServerTimezone
    this.serverTimezoneOffset = 0;
    this.serverTimezoneName = 'GMT';

    if (typeof this.jsDateLocal.getMonth !== 'function') {
      throw new Error(`Invalid date input: ${input.toString()}`);
    }

    this.jsDateGMT = new Date(this.jsDateLocal.valueOf());
    this.jsDateGMT.setMinutes(this.jsDateLocal.getMinutes() - this.localTimezoneOffset);
  }

  /**
   * Intended for testing purposes so we can easilly pretend to be elsewhere
   * @param {number} offset - offset from GMT in minutes
   * @param {string} name - e.g. "Europe/Moscow"
   */
  setLocalTimezone(offset, name) {
    if (typeof offset === 'number' && typeof name === 'string') {
      this.localTimezoneOffset = offset;
      this.localTimezoneName = name;

      this.jsDateLocal = new Date(this.jsDateGMT.valueOf());
      this.jsDateLocal.setMinutes(this.jsDateGMT.getMinutes() + this.localTimezoneOffset);
    } else {
      throw new Error('Provide an offset in number of minutes, and a string of the timezone name');
    }
  }

  /**
   * Updates the server timezone, used in calculating formatted server time
   * @param {number} offset - the offset in minutes, in the style of js Date object timezoneOffset
   * @param {string} name - the name of the server timezone (e.g. Europe/London)
   */
  setServerTimezone(offset, name) {
    if (typeof offset === 'number' && typeof name === 'string') {
      this.serverTimezoneOffset = offset;
      this.serverTimezoneName = name;
    } else {
      throw new Error('Provide an offset in number of minutes, and a string of the timezone name');
    }
  }

  /**
   * Generates long-format string of local datetime
   * - e.g. 17:00 Shanghai / Pacific time, Thursday 19th March 2020
   * @returns {string}
   */
  longLocal() {
    return stringify(this.jsDateLocal, this.localTimezoneName);
  }

  /**
   * Generates short-format string of local datetime
   * - e.g. 17:00 Shanghai / Pacific time, Thu 19th Mar '20
   * @returns {string}
   */
  shortLocal() {
    return stringify(this.jsDateLocal, this.localTimezoneName, true);
  }

  /**
   * Takes an element of the format <span class="jddt" millis="[milliseconds]"
   * format="[validFormat]"></span> and populates its innerHTML with the JDDT
   * @static
   * @param {HTMLElement} el
   */
  static applyToElement(el) {
    if (!el.hasAttribute('data-server-timezone-offset') || !el.hasAttribute('data-server-timezone-name')) {
      throw new Error('Element with .jddt class did not have data-server-timezone-offset '
          + 'and data-server-timezone-name attributes');
    }
    const millis = parseInt(el.dataset.millis, 10);
    const jddt = new JDDT(millis);
    jddt.setServerTimezone(parseInt(el.dataset.serverTimezoneOffset, 10),
      el.dataset.serverTimezoneName);
    if (jddt.localTimezoneName !== jddt.serverTimezoneName) {
      const format = el.hasAttribute('data-format')
        ? el.dataset.format
        : 'longLocal';

      if (typeof JDDT.prototype[format] !== 'function') {
        throw new Error(`Invalid JDDT format specified on element: ${format}`);
      }

      // eslint-disable-next-line no-param-reassign
      el.innerHTML = jddt[format]();
    }
  }

  /**
   * Takes an element of the format <span class="jddt-range" data-from-millis="[milliseconds]"
   * data-to-millis="[milliseconds]" short="boolean"></span> and populates its innerHTML with
   * the JDDT
   * @static
   * @param {HTMLElement} el
   */
  static applyToRangeElement(el) {
    if (!el.hasAttribute('data-server-timezone-offset') || !el.hasAttribute('data-server-timezone-name')) {
      throw new Error('Element with .jddt-range class did not have data-server-timezone-offset '
        + 'and data-server-timezone-name attributes');
    }

    const fromMillis = parseInt(el.dataset.fromMillis, 10);
    const toMillis = parseInt(el.dataset.toMillis, 10);
    const short = el.hasAttribute('data-short') && el.dataset.short !== 'false';

    const fromJDDT = new JDDT(fromMillis);
    const toJDDT = new JDDT(toMillis);

    fromJDDT.setServerTimezone(parseInt(el.dataset.serverTimezoneOffset, 10),
      el.dataset.serverTimezoneName);

    toJDDT.setServerTimezone(fromJDDT.serverTimezoneOffset, fromJDDT.serverTimezoneName);

    if (fromJDDT.localTimezoneName !== fromJDDT.serverTimezoneName) {
      // eslint-disable-next-line no-param-reassign
      el.innerHTML = stringifyDateRange(
        fromJDDT.jsDateLocal,
        toJDDT.jsDateLocal,
        fromJDDT.localTimezoneName,
        short,
      );
    }
  }

  /**
   * Looks for anything of the format <span class='jddt' data-millis='[epochmillis]'
   * data-format='[validformat]' data-server-timezone-offset='[milliseconds]
   * data-server-timezone-name='[zone name e.g. Europe/London]'> and populates
   * its innerHTML with the formatted date
   * @param {Node} container
   */
  static initialise(container) {
    if (typeof MutationObserver !== 'undefined') {
      // Should exist in IE11
      const observer = new MutationObserver((objects) => {
        // expect Babel for this
        objects.forEach((mutationRecord) => {
          if (mutationRecord.type === 'childList') {
            for (let i = 0; i < mutationRecord.target.children.length; i += 1) {
              mutationRecord.target.children[i].querySelectorAll('.jddt[data-millis]')
                .forEach((jddtElement) => {
                  this.applyToElement(jddtElement);
                });
              mutationRecord.target.children[i].querySelectorAll('.jddt-range')
                .forEach((jddtRangeElement) => {
                  this.applyToRangeElement(jddtRangeElement);
                });
            }
          }
        });
      });
      observer.observe(container, {
        childList: true,
        subtree: true,
      });
    } else {
      // IE9 +
      document.addEventListener('DOMContentLoaded', () => {
        document.querySelectorAll('.jddt[data-millis]').forEach((jddtElement) => {
          this.applyToElement(jddtElement);
        });
        document.querySelectorAll('.jddt-range').forEach((jddtRangeElement) => {
          this.applyToRangeElement(jddtRangeElement);
        });
      });
    }
  }
}