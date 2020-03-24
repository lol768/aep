export default function msToHumanReadable(duration) {
  const arrayToSentence = arr => {
    if (!arr) return;
    const {length} = arr;
    if (length === 0) return;
    if (length === 1) return arr.pop();
    if (length === 2) return arr.join(' and ');
    const last = arr.pop();
    return `${arr.join(', ')} and ${last}`;
  };

  return arrayToSentence(Object.entries({
    day: Math.floor((duration / (1000 * 60 * 60 * 24)) % 24),
    hour: Math.floor((duration / (1000 * 60 * 60)) % 24),
    minute: Math.floor((duration / (1000 * 60)) % 60),
  }).map(([unit, quantity]) => {
    if (quantity === 0) return null;
    return (quantity % 2 === 0) ? `${quantity} ${unit}s` : `${quantity} ${unit}`;
  }).filter(Boolean));
}
