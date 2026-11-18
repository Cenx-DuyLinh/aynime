(function () {
  if (window.animeThorVideoBridgeStarted) {
    return;
  }
  window.animeThorVideoBridgeStarted = true;
  if (typeof animeThorVideo === "undefined") {
    return;
  }

  function videoArea(video) {
    const box = video.getBoundingClientRect();
    return box.width * box.height;
  }

  function mainVideo() {
    let biggestVideo = null;
    for (const video of document.querySelectorAll("video")) {
      if (video.readyState < 1) {
        continue;
      }
      if (biggestVideo === null || videoArea(video) > videoArea(biggestVideo)) {
        biggestVideo = video;
      }
    }
    return biggestVideo;
  }

  function reportVideo() {
    const video = mainVideo();
    if (video === null) {
      return;
    }
    animeThorVideo.postMessage(JSON.stringify({
      seconds: video.currentTime,
      paused: video.paused,
      rate: video.playbackRate,
      area: videoArea(video)
    }));
  }

  function seekWithSitePlayer(seconds) {
    try {
      if (typeof window.jwplayer === "function" && window.jwplayer() && window.jwplayer().seek) {
        window.jwplayer().seek(seconds);
        return true;
      }
      if (window.videojs && typeof window.videojs.getPlayers === "function") {
        const players = Object.values(window.videojs.getPlayers()).filter(Boolean);
        if (players.length > 0) {
          players[0].currentTime(seconds);
          return true;
        }
      }
    } catch (error) {
      return false;
    }
    return false;
  }

  function seekVideoElement(video, seconds) {
    const wasPlaying = !video.paused;
    video.pause();
    video.addEventListener("seeked", function resumeAfterSeek() {
      if (wasPlaying) {
        video.play();
      }
    }, { once: true });
    video.currentTime = seconds;
  }

  function runCommand(event) {
    const command = JSON.parse(event.data);
    const video = mainVideo();
    if (video === null) {
      return;
    }
    if (command.name === "seek" && !seekWithSitePlayer(command.seconds)) {
      seekVideoElement(video, command.seconds);
    }
    if (command.name === "pause") {
      video.pause();
    }
    if (command.name === "togglePlay") {
      if (video.paused) {
        video.play();
      } else {
        video.pause();
      }
    }
  }

  function episodeFromWatchingText() {
    const pageText = document.body ? document.body.innerText.slice(0, 20000) : "";
    const match = pageText.match(/watching\s+(?:episode|ep\.?)\s*(\d{1,4})/i);
    return match ? Number(match[1]) : null;
  }

  function episodeFromHighlightedButton() {
    const highlighted = document.querySelectorAll(
      '[class*="active"], [class*="current"], [class*="selected"], [aria-current="true"], [aria-current="page"]'
    );
    for (const element of highlighted) {
      const label = (element.innerText || "").trim();
      const looksLikeEpisodeButton = /^(?:ep(?:isode)?\.?\s*)?\d{1,4}$/i.test(label);
      const isInsideEpisodeList = element.closest('[class*="ep"], [id*="ep"]') !== null;
      if (looksLikeEpisodeButton && isInsideEpisodeList) {
        return Number(label.match(/\d{1,4}/)[0]);
      }
    }
    return null;
  }

  const NAME_ELEMENTS =
    '.SubTitle, .sub-title, .other-name, .name-other, .othername, .alias, .original-name, [itemprop="alternateName"]';
  const MAXIMUM_NAME_HINTS = 4;
  let lastSentNameHints = "";

  function shortText(element) {
    const text = (element && element.textContent ? element.textContent : "").replace(/\s+/g, " ").trim();
    return text.length >= 3 && text.length <= 200 ? text : null;
  }

  function lineUnderMainHeading() {
    const heading = document.querySelector("h1");
    const next = heading ? heading.nextElementSibling : null;
    return next && /^(H2|H3|P|DIV|SPAN)$/.test(next.tagName) ? shortText(next) : null;
  }

  function nameHintsFromPage() {
    const hints = [];
    document.querySelectorAll(NAME_ELEMENTS).forEach(function (element) {
      const text = shortText(element);
      if (text && hints.indexOf(text) < 0) {
        hints.push(text);
      }
    });
    const underHeading = lineUnderMainHeading();
    if (underHeading && hints.indexOf(underHeading) < 0) {
      hints.push(underHeading);
    }
    return hints.slice(0, MAXIMUM_NAME_HINTS);
  }

  function reportNameHints() {
    const hints = nameHintsFromPage();
    const asText = JSON.stringify(hints);
    if (hints.length === 0 || asText === lastSentNameHints) {
      return;
    }
    lastSentNameHints = asText;
    animeThorVideo.postMessage(JSON.stringify({ nameHints: hints }));
  }

  function reportEpisodeHint() {
    if (window !== window.top) {
      return;
    }
    reportNameHints();
    const episode = episodeFromWatchingText() ?? episodeFromHighlightedButton();
    if (episode !== null) {
      animeThorVideo.postMessage(JSON.stringify({ episodeHint: episode }));
    }
  }

  let lastEventReportMs = 0;

  function reportOnPlayerEvent() {
    const nowMs = Date.now();
    if (nowMs - lastEventReportMs < 200) {
      return;
    }
    lastEventReportMs = nowMs;
    reportVideo();
  }

  function reportPlayOrPauseAtOnce() {
    lastEventReportMs = Date.now();
    reportVideo();
  }

  for (const eventName of ["timeupdate", "seeked", "playing"]) {
    document.addEventListener(eventName, reportOnPlayerEvent, true);
  }
  for (const eventName of ["play", "pause"]) {
    document.addEventListener(eventName, reportPlayOrPauseAtOnce, true);
  }

  animeThorVideo.addEventListener("message", runCommand);
  setInterval(reportVideo, 250);
  setInterval(reportEpisodeHint, 2000);
})();
