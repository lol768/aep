/* eslint-env browser */
/**
 * Entrypoint for the admin section of the app.
 */

import './polyfills';
import './error-reporter-init';

import $ from 'jquery';
import Tablesort from 'tablesort';
import JDDT from './jddt';
import * as flexiPicker from './flexi-picker';
import * as dateTimePicker from './date-time-picker';
import './double-submit-protection';

import '@universityofwarwick/statuspage-widget/dist/main';
import './admin/assessment-setup';

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

  $('.datetimepicker', $scope).each((i, container) => {
    dateTimePicker.DateTimePicker(container);
  });

  $('table.table-sortable', $scope).each((i, table) => {
    Tablesort(table);
  });
}

JDDT.initialise(document);

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

  if (document.querySelectorAll('.studentAssessmentInfo').length > 0) {
    import('./studentAssessmentInfo');
  }

  if (document.body.classList.contains('allAnnouncementsAndQueries')) {
    import('./admin/assessment-announcements-and-queries');
  }

  if (document.body.classList.contains('generating-zip')) {
    setTimeout(() => {
      document.querySelectorAll('.fa-spinner-third').forEach((node) => node.parentNode.removeChild(node));
      window.location.reload();
    }, 5000);
  }
});
