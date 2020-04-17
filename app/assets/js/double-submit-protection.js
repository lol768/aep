function attachListener(form) {
  if (form.classList.contains('double-submit-protection-init')) {
    return true;
  }

  form.addEventListener('submit', (e) => {
    const submitted = form.getAttribute('data-submitted');

    if (submitted) {
      e.preventDefault();
      return false;
    }

    form.setAttribute('data-submitted', true);

    form.querySelectorAll('.btn').forEach((node) => {
      const btn = node;
      if (!btn.disabled) {
        btn.classList.add('disabled');
        btn.classList.add('double-submit-disabled');
        btn.disabled = true;
      }
    });

    document.addEventListener('pageshow', () => {
      form.setAttribute('data-submitted', false);

      form.querySelectorAll('.btn.double-submit-disabled').forEach((node) => {
        const btn = node;
        btn.classList.remove('disabled');
        btn.classList.remove('double-submit-disabled');
        btn.disabled = false;
      });
    });

    return true;
  });

  form.classList.add('double-submit-protection-init');
  return true;
}

if (typeof MutationObserver !== 'undefined') {
  // Should exist in IE11
  const observer = new MutationObserver(() => {
    document.body.querySelectorAll('form.double-submit-protection').forEach(attachListener);
  });
  observer.observe(document.body, {
    childList: true,
    subtree: true,
  });
} else {
  // IE9 +
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('form.double-submit-protection').forEach(attachListener);
  });
}
