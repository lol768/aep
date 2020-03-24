export const msToHumanReadable = (duration) => {
  // let seconds = Math.floor((duration / 1000) % 60);
  // let minutes = Math.floor((duration / (1000 * 60)) % 60);
  // let hours = Math.floor((duration / (1000 * 60 * 60)) % 24);
  // let days = Math.floor((duration / (1000 * 60 * 60 * 24)) % 24);
  //
  // // minutes = (hours % 2 === 0) ? `${minutes} minutes` : `${minutes} minute`;
  // // hours = (hours % 2 === 0) ? `${hours} hours` : `${hours} hour`;
  // // days = (days % 2 === 0) ? `${days} days` : `${days} day`;
  //
  // // const plural = (quantity, unit) => {
  // //   (quantity % 2 === 0) ? `${quantity} ${unit}s` : `${hours} ${unit}`;
  // // };
  // //
  // // const data = {
  // //   minute: Math.floor((duration / (1000 * 60)) % 60),
  // //   hour: Math.floor((duration / (1000 * 60 * 60)) % 24),
  // //   day: Math.floor((duration / (1000 * 60 * 60 * 24)) % 24),
  // // }

  return Object.entries({
    day: Math.floor((duration / (1000 * 60 * 60 * 24)) % 24),
    hour: Math.floor((duration / (1000 * 60 * 60)) % 24),
    minute: Math.floor((duration / (1000 * 60)) % 60),
  }).map(([unit, quantity]) => {
    if (quantity === 0) return;
    return (quantity % 2 === 0) ? `${quantity} ${unit}s` : `${quantity} ${unit}`;
  }).join(' ');
};
