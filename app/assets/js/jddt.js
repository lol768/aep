// John Dale Datetime (JDDT)

/**
 * John Dale Datetime object constructor. Works with anything accepted by a plain JS Date object.
 * @constructor
 * @param {Date|number|string} input - accepts a Date, a number of epoch milliseconds
 * or a suitably formatted string
 */
export default function JDDT(input) {
  if (input instanceof Date) {
    this.jsDateLocal = input;
  } else {
    this.jsDateLocal = new Date(input);
  }

  if (typeof this.jsDateLocal.getMonth !== 'function') {
    throw new Error(`Invalid date input: ${input.toString()}`);
  }

  this.jsDateGMT = new Date(this.jsDateLocal.valueOf());
  this.jsDateGMT.setMinutes(this.jsDateLocal.getMinutes() + JDDT.localTimezoneOffset);
}

/**
 * Takes number n, returns as string with extra 0 at the start if it's 1 digit long
 * @static
 * @param {number} n - Should be a positive integer otherwise results will be odd
 * @returns {string}
 */
JDDT.pad0 = function pad0(n) {
  return n < 10 ? `0${n}` : n.toString();
};

/**
 * Takes a number n, returns as a string with "st", "nd", "rd" or "th" appended as appropriate.
 * @static
 * @param {number} n - Only positive integers, no more than 2 digits because this is for dates
 * @returns {string}
 */
JDDT.th = function th(n) {
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
};

/**
 * Stores the users local timezone offset
 * @constant
 */
JDDT.localTimezoneOffset = new Date().getTimezoneOffset();

/**
 * Month names
 * @constant
 * @static
 */
JDDT.months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/**
 * Day names
 * @constant
 * @static
 */
JDDT.days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

// Get nice local timezone name if it exists
if (Intl !== undefined && Intl.DateTimeFormat !== undefined) {
  JDDT.dtf = Intl.DateTimeFormat();
  if (JDDT.dtf.resolvedOptions !== undefined && JDDT.dtf.resolvedOptions().timeZone !== undefined) {
    JDDT.localTimezoneName = JDDT.dtf.resolvedOptions().timeZone;
  }
}

// For users on old rubbish browsers fall back to +/- mm:hh for timezone name
if (JDDT.localTimezoneName === undefined) {
  JDDT.localTimezoneName = `${JDDT.localTimezoneOffset < 0 ? '-' : '+'}
  ${JDDT.pad0(Math.floor(Math.abs(JDDT.localTimezoneOffset / 60)))}:
  ${JDDT.pad0(Math.abs(JDDT.localTimezoneOffset) % 60)}`;
}

/**
 * Generates long/short JDDT format with timezoneName appended
 * @private
 * @param {Date} date - js Date object to format
 * @param {string} timezoneName - name of timezone to be appended
 * @param {boolean} [short] - if this is true the function will return truncated
 * months, days and years
 * @returns {string}
 */
JDDT.prototype.stringify = function stringify(date, timezoneName, short) {
  return `${JDDT.pad0(date.getHours())}:${JDDT.pad0(date.getMinutes())} ${timezoneName}, 
  ${short ? JDDT.days[date.getDay()].substring(0, 3) : JDDT.days[date.getDay()]} 
  ${JDDT.th(date.getDate())} 
  ${short ? JDDT.months[date.getMonth()].substring(0, 3) : JDDT.months[date.getMonth()]} 
  ${short ? `'${date.getFullYear().toString().substring(2, 4)}` : date.getFullYear().toString()}`;
};

/**
 * Generates long-format string of local datetime
 * - e.g. 17:00 Shanghai / Pacific time, Thursday 19th March 2020
 * @returns {string}
 */
JDDT.prototype.longLocal = function longLocal() {
  return this.stringify(this.jsDateLocal, JDDT.localTimezoneName);
};

/**
 * Generates long-format string of GMT datetime
 * - e.g. 12:00 GMT, Thursday 19th March 2020
 * @returns {string}
 */
JDDT.prototype.longGMT = function longGMT() {
  return this.stringify(this.jsDateGMT, 'GMT');
};

/**
 * Generates short-format string of local datetime
 * - e.g. 17:00 Shanghai / Pacific time, Thu 19th Mar '20
 * @returns {string}
 */
JDDT.prototype.shortLocal = function shortLocal() {
  return this.stringify(this.jsDateLocal, JDDT.localTimezoneName, true);
};

/**
 * Generates short-format string of GMT datetime
 * - e.g. 12:00 GMT, Thu 19th Mar '20
 * @returns {string}
 */
JDDT.prototype.shortGMT = function shortGMT() {
  return this.stringify(this.jsDateGMT, 'GMT', true);
};

/**
 * Convenience method that returns true if the user's local timezone is GMT
 * @returns {boolean}
 */
JDDT.prototype.isGMT = function isGMT() {
  return JDDT.localTimezoneOffset === 0;
};
