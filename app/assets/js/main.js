/* eslint-env browser */
import './polyfills';

import $ from 'jquery';
import 'bootstrap/js/tooltip';
import 'bootstrap/js/popover';
import '@universityofwarwick/id7/js/account-popover.jquery';

import * as flexiPicker from './flexi-picker';

require('@universityofwarwick/statuspage-widget/dist/main');

/**
 * Attach handlers to all elements inside $scope. All jQuery selects
 * must be scoped to $scope, and you should only call bindTo on content
 * that you know has not had handlers attached already (either a whole page
 * that's just been loaded, or a piece of HTML you've just loaded into the
 * document dynamically)
 */
function bindTo($scope) {
  $('[data-toggle="popover"]', $scope).popover();

  flexiPicker.bindTo($scope);
}

$(() => {
  const $html = $('html');

  // Apply to all content loaded non-AJAXically
  bindTo($('#main'));

  // Any selectors below should only be for things that we know won't be inserted into the
  // page after DOM ready.

  // Dismiss popovers when clicking away
  function closePopover($popover) {
    const $creator = $popover.data('creator');
    if ($creator) {
      $creator.popover('hide');
    }
  }

  $html
    .on('shown.bs.popover', (e) => {
      const $po = $(e.target).popover().data('bs.popover').tip();
      $po.data('creator', $(e.target));
    })
    .on('click.popoverDismiss', (e) => {
      const $target = $(e.target);

      // if clicking anywhere other than the popover itself
      if ($target.closest('.popover').length === 0 && $(e.target).closest('.has-popover').length === 0) {
        $('.popover').each((i, popover) => closePopover($(popover)));
      } else if ($target.closest('.close').length > 0) {
        closePopover($target.closest('.popover'));
      }
    })
    .on('keyup.popoverDismiss', (e) => {
      const key = e.which || e.keyCode;
      if (key === 27) {
        $('.popover').each((i, popover) => closePopover($(popover)));
      }
    });
});
