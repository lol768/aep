function setVisibilityByClassName(className, visible) {
  document.querySelectorAll(`.${className}`).forEach((node) => {
    if (visible) {
      node.classList.remove('hide');
    } else {
      node.classList.add('hide');
    }
  });
}

export default function showWSConnectivity(websocket) {
  websocket.add({
    onConnect: () => {
      setVisibilityByClassName('ws-connected', true);
      setVisibilityByClassName('ws-disconnected', false);
      setVisibilityByClassName('ws-error', false);
    },
    onError: () => {
      setVisibilityByClassName('ws-connected', false);
      setVisibilityByClassName('ws-error', true);
    },
    onClose: () => {
      setVisibilityByClassName('ws-connected', false);
      setVisibilityByClassName('ws-disconnected', true);
    },
    onData: (d) => {
      if (d.type === 'UpdateConnectivityIndicator') {
        const signalStrength = d.signalStrength || 5;
        const indicatorClass = (signalStrength >= 1 && signalStrength < 5) ? `fa-signal-${signalStrength}` : 'fa-signal';

        document.querySelectorAll('.connectivity-indicator').forEach((indicator) => {
          if (indicator.classList.contains(indicatorClass)) return;

          // Remove any existing fa-signal* classes
          indicator.classList.forEach((cls) => {
            if (cls.startsWith('fa-signal')) {
              indicator.classList.remove(cls);
            }

            indicator.classList.add(indicatorClass);
          });
        });
      }
    },
  });
}
