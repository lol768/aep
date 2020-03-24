export default function msToHumanReadable(duration) {
  return Object.entries({
    day: Math.floor((duration / (1000 * 60 * 60 * 24)) % 24),
    hour: Math.floor((duration / (1000 * 60 * 60)) % 24),
    minute: Math.floor((duration / (1000 * 60)) % 60),
  }).map(([unit, quantity]) => {
    if (quantity === 0) return null;
    return (quantity % 2 === 0) ? `${quantity} ${unit}s` : `${quantity} ${unit}`;
  }).filter(Boolean).join(' ');
}
