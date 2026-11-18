const appBridge = (function () {
  const waitingReplies = new Map();
  let nextRequestId = 1;
  const isInsideApp = typeof animeThorApp !== "undefined";

  if (isInsideApp) {
    animeThorApp.addEventListener("message", function (event) {
      const reply = JSON.parse(event.data);
      const resolve = waitingReplies.get(reply.requestId);
      if (resolve) {
        waitingReplies.delete(reply.requestId);
        resolve(reply);
      }
    });
  }

  function tell(message) {
    if (isInsideApp) {
      animeThorApp.postMessage(JSON.stringify(message));
    }
  }

  function ask(message) {
    if (!isInsideApp) {
      return Promise.resolve({ savedSites: [] });
    }
    return new Promise(function (resolve) {
      const requestId = nextRequestId;
      nextRequestId += 1;
      waitingReplies.set(requestId, resolve);
      animeThorApp.postMessage(JSON.stringify(Object.assign({}, message, { requestId: requestId })));
    });
  }

  return { tell: tell, ask: ask };
})();

function escapeHtml(text) {
  const element = document.createElement("span");
  element.textContent = text == null ? "" : String(text);
  return element.innerHTML;
}

function formatSeconds(totalSeconds) {
  const wholeSeconds = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(wholeSeconds / 60);
  const seconds = String(wholeSeconds % 60).padStart(2, "0");
  return minutes + ":" + seconds;
}
