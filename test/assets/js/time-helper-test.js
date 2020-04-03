import msToHumanReadable from 'time-helper';

describe('msToHumanReadable', () => {
  it('handles times < 1 minute', () => {
    assert.equal(msToHumanReadable(0), 'a moment');
    assert.equal(msToHumanReadable(59000), 'a moment');
  });

  it('handles minutes and hours', () => {
    assert.equal(msToHumanReadable(60000), '1 minute');
    assert.equal(msToHumanReadable(60000 * 2), '2 minutes');
    assert.equal(msToHumanReadable(60000 * 60), '1 hour');
    assert.equal(msToHumanReadable(60000 * 60 * 2 + 60000), '2 hours and 1 minute');
    assert.equal(msToHumanReadable(60000 * 60 * 2 + 60000 * 2), '2 hours and 2 minutes');
  })

  it('handles whole days as 24 hours', () => {
    assert.equal(msToHumanReadable(60000 * 60 * 24), '1 day');
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 2), '2 days');
  });

  it('combines units without using an Oxford comma', () => {
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 2 + 60000 * 60 * 2 + 60000), '2 days, 2 hours and 1 minute');
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 2 + 60000 * 60 + 60000), '2 days, 1 hour and 1 minute');
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 5 + 60000 * 60 + 60000), '5 days, 1 hour and 1 minute');
    assert.equal(msToHumanReadable(432946802), '5 days and 15 minutes');
    assert.equal(msToHumanReadable(432946802), '5 days and 15 minutes');
  });

  it('displays the correct number of days', () => {
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 30 + 60000 * 60 + 60000), '30 days, 1 hour and 1 minute');
  });
});
