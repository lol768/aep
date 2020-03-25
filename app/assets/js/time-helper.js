const arrayToSentence = (arr) => {
  if (!arr) return null;
  if (arr.length === 0) return null;
  const [last, ...rest] = arr.reverse();
  if (arr.length === 1) return last;
  return `${rest.reverse().join(', ')} and ${last}`;
};

export default function msToHumanReadable(duration) {
  return arrayToSentence(Object.entries({
    day: Math.floor((duration / (1000 * 60 * 60 * 24)) % 24),
    hour: Math.floor((duration / (1000 * 60 * 60)) % 24),
    minute: Math.floor((duration / (1000 * 60)) % 60),
  }).map(([unit, quantity]) => {
    if (quantity === 0) return null;
    return (quantity > 1) ? `${quantity} ${unit}s` : `${quantity} ${unit}`;
  }).filter(Boolean));
}
