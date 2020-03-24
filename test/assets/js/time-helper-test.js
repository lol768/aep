import msToHumanReadable from 'time-helper';

describe('time-helper', () => {
  it('msToHumanReadable', () => {
    assert.equal(msToHumanReadable(60000), '1 minute');
    assert.equal(msToHumanReadable(60000 * 2), '2 minutes');
    assert.equal(msToHumanReadable(60000 * 60), '1 hour');
    assert.equal(msToHumanReadable(60000 * 60 * 2 + 60000), '2 hours and 1 minute');
    assert.equal(msToHumanReadable(60000 * 60 * 2 + 60000 * 2), '2 hours and 2 minutes');
    assert.equal(msToHumanReadable(60000 * 60 * 24), '1 day');
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 2), '2 days');
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 2 + 60000 * 60 * 2 + 60000), '2 days, 2 hours and 1 minute');
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 2 + 60000 * 60 + 60000), '2 days, 1 hour and 1 minute');
    assert.equal(msToHumanReadable(60000 * 60 * 24 * 5 + 60000 * 60 + 60000), '5 days, 1 hour and 1 minute');
    assert.equal(msToHumanReadable(432946802), '5 days and 15 minutes');
  });
});
