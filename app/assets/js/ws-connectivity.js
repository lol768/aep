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
  });
}
