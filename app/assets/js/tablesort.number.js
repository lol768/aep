import Tablesort from 'tablesort';

function cleanNumber(i) {
  return i.replace(/[^\-?0-9.]/g, '');
}

function compareNumber(a, b) {
  const floatA = Number.parseFloat(a);
  const floatB = Number.parseFloat(b);

  return (Number.isNaN(floatA) ? 0 : floatA) - (Number.isNaN(floatB) ? 0 : floatB);
}

export default function apply() {
  Tablesort.extend(
    'number',
    (item) => item.match(/^[-+]?[£\x24Û¢´€]?\d+\s*([,.]\d{0,2})/) // Prefixed currency
      || item.match(/^[-+]?\d+\s*([,.]\d{0,2})?[£\x24Û¢´€]/) // Suffixed currency
      || item.match(/^[-+]?(\d)*-?([,.]){0,1}-?(\d)+([E,e][-+][\d]+)?%?$/), // Number
    (a, b) => compareNumber(cleanNumber(b), cleanNumber(a)),
  );
}
