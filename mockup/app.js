const VIDEO_LENGTH_SECONDS = 54;
const SUBTITLE_FILE_DELAY_SECONDS = 1.2;
const SECONDS_OF_AUDIO_NEEDED_FOR_SYNC = 3;
const PLAYER_TICK_SECONDS = 0.1;
const PLAYTRANSLATE_READ_DELAY_MS = 600;
const SPEECH_LENGTH_SECONDS = 3.6;
const SECONDS_BETWEEN_LINES = 5;
const FIRST_LINE_START_SECONDS = 1.5;

const state = {
  history: [],
  currentPage: null,
  savedSites: loadStoredValue("savedSites", []),
  continueWatching: loadStoredValue("continueWatching", null),
  settings: loadStoredValue("settings", { searchEngine: "Google", theme: "dark", subtitleSize: "medium" }),
  player: null,
  playTranslate: { lineIndex: null, pendingLineIndex: null, selectedTokenIndex: null },
};

function loadStoredValue(key, fallback) {
  const storedText = localStorage.getItem("anime-mockup-" + key);
  return storedText ? JSON.parse(storedText) : fallback;
}

function storeValue(key, value) {
  localStorage.setItem("anime-mockup-" + key, JSON.stringify(value));
}

function escapeHtml(text) {
  const element = document.createElement("span");
  element.textContent = text;
  return element.innerHTML;
}

function formatSeconds(totalSeconds) {
  const wholeSeconds = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(wholeSeconds / 60);
  const seconds = String(wholeSeconds % 60).padStart(2, "0");
  return minutes + ":" + seconds;
}

function formatOffset(offsetSeconds) {
  const sign = offsetSeconds >= 0 ? "+" : "−";
  return sign + Math.abs(offsetSeconds).toFixed(1) + " s";
}

function findAnime(animeId) {
  return ANIME_CATALOG.find((anime) => anime.id === animeId);
}

function siteNameFromDomain(domain) {
  return domain.split(".")[0];
}

function isSiteSaved(domain) {
  return state.savedSites.some((site) => site.domain === domain);
}

function looksLikeAddress(text) {
  return /^[^\s]+\.[a-z]{2,}(\/\S*)?$/i.test(text);
}

function domainFromAddress(address) {
  return address.replace(/^https?:\/\//i, "").split("/")[0].toLowerCase();
}

function numberFromText(text) {
  let total = 0;
  for (const character of text) {
    total = (total * 31 + character.charCodeAt(0)) % 9973;
  }
  return total;
}

function blockedAdCount(domain) {
  return 3 + (numberFromText(domain) % 10);
}

function animeMatchingQuery(query) {
  const lowerQuery = query.trim().toLowerCase();
  if (!lowerQuery) {
    return [];
  }
  return ANIME_CATALOG.filter((anime) =>
    [anime.titleJapanese, anime.titleEnglish, anime.titleRomaji].some((title) => title.toLowerCase().includes(lowerQuery))
  );
}

function savedSitesMatchingQuery(query) {
  const lowerQuery = query.trim().toLowerCase();
  return state.savedSites.filter((site) => site.domain.includes(lowerQuery) || site.name.toLowerCase().includes(lowerQuery));
}

function speechStartSeconds(lineIndex) {
  return FIRST_LINE_START_SECONDS + lineIndex * SECONDS_BETWEEN_LINES;
}

function speechEndSeconds(lineIndex) {
  return speechStartSeconds(lineIndex) + SPEECH_LENGTH_SECONDS;
}

function subtitleStartSeconds(lineIndex, offsetSeconds) {
  return speechStartSeconds(lineIndex) + SUBTITLE_FILE_DELAY_SECONDS + offsetSeconds;
}

function subtitleEndSeconds(lineIndex, offsetSeconds) {
  return speechEndSeconds(lineIndex) + SUBTITLE_FILE_DELAY_SECONDS + offsetSeconds;
}

function lineIndexSpokenAt(seconds) {
  const index = SUBTITLE_LINES.findIndex((line, lineIndex) =>
    seconds >= speechStartSeconds(lineIndex) && seconds < speechEndSeconds(lineIndex)
  );
  return index === -1 ? null : index;
}

function subtitleLineIndexAt(seconds, offsetSeconds) {
  const index = SUBTITLE_LINES.findIndex((line, lineIndex) =>
    seconds >= subtitleStartSeconds(lineIndex, offsetSeconds) && seconds < subtitleEndSeconds(lineIndex, offsetSeconds)
  );
  return index === -1 ? null : index;
}

function lastStartedSubtitleLineIndex(seconds, offsetSeconds) {
  let lastStartedIndex = -1;
  SUBTITLE_LINES.forEach((line, lineIndex) => {
    if (subtitleStartSeconds(lineIndex, offsetSeconds) <= seconds + 0.05) {
      lastStartedIndex = lineIndex;
    }
  });
  return lastStartedIndex;
}

function openPage(page, options = {}) {
  if (state.currentPage && !options.skipHistory) {
    state.history.push(state.currentPage);
  }
  leavePlayerIfNeeded(page);
  ensurePlayerForPage(page);
  state.currentPage = page;
  renderCurrentPage();
}

function goBack() {
  const previousPage = state.history.pop();
  if (previousPage) {
    openPage(previousPage, { skipHistory: true });
  }
}

function goHome() {
  openPage({ type: "home" });
}

function leavePlayerIfNeeded(nextPage) {
  if (state.player && nextPage.type !== "player") {
    saveContinueWatching();
    state.player = null;
  }
}

function renderCurrentPage() {
  const page = state.currentPage;
  const pageElement = document.getElementById("page");
  pageElement.innerHTML = buildPageHtml(page);
  pageElement.scrollTop = 0;
  updateAddressBar(page);
  updateSiteControls(page);
  renderDetectionBar();
  if (page.type === "player") {
    updatePlayerView();
  }
}

function buildPageHtml(page) {
  const builders = {
    home: buildHomePage,
    search: buildSearchPage,
    "web-results": buildWebResultsPage,
    site: buildSitePage,
    "site-anime": buildSiteAnimePage,
    player: buildPlayerPage,
    settings: buildSettingsPage,
  };
  return builders[page.type](page);
}

function pageAddress(page) {
  const addresses = {
    home: () => "",
    search: () => page.query,
    "web-results": () => searchEngineAddress(page.query),
    site: () => page.domain + (page.query ? "/search?q=" + page.query : ""),
    "site-anime": () => page.domain + "/anime/" + page.animeId,
    player: () => page.domain + "/watch/" + page.animeId + "/episode-" + page.episode,
    settings: () => "",
  };
  return addresses[page.type]();
}

function searchEngineAddress(query) {
  const engineAddresses = {
    Google: "google.com/search?q=",
    DuckDuckGo: "duckduckgo.com/?q=",
    Bing: "bing.com/search?q=",
  };
  return engineAddresses[state.settings.searchEngine] + query;
}

function updateAddressBar(page) {
  const addressBar = document.getElementById("address-bar");
  addressBar.value = pageAddress(page);
  addressBar.blur();
}

function pageBelongsToSite(page) {
  return ["site", "site-anime", "player"].includes(page.type);
}

function updateSiteControls(page) {
  const starButton = document.getElementById("star-button");
  const blockedPill = document.getElementById("blocked-pill");
  const onSite = pageBelongsToSite(page);
  starButton.classList.toggle("hidden", !onSite);
  blockedPill.classList.toggle("hidden", !onSite);
  if (!onSite) {
    return;
  }
  const saved = isSiteSaved(page.domain);
  starButton.innerHTML = saved ? "&#9733;" : "&#9734;";
  starButton.classList.toggle("is-saved", saved);
  starButton.title = saved ? "Remove from saved sites" : "Save site";
  blockedPill.textContent = blockedAdCount(page.domain) + " ads blocked";
}

function buildHomePage() {
  return `
    <div class="app-page">
      <h1 class="page-heading">Start</h1>
      <div class="section-heading">Saved sites</div>
      ${buildSavedSitesGrid()}
      <div class="section-heading">Continue watching</div>
      ${buildContinueWatchingCard()}
    </div>`;
}

function buildSavedSitesGrid() {
  if (state.savedSites.length === 0) {
    return `<div class="card empty-note">Nothing saved yet. Open a site and tap &#9734; in the top bar to keep it here.</div>`;
  }
  const tiles = state.savedSites.map((site) => `
    <div class="card clickable saved-site-tile" data-action="open-saved-site" data-domain="${escapeHtml(site.domain)}">
      <div class="site-badge">${escapeHtml(site.name.charAt(0).toUpperCase())}</div>
      <div>${escapeHtml(site.name)}</div>
    </div>`);
  return `<div class="saved-sites-grid">${tiles.join("")}</div>`;
}

function buildContinueWatchingCard() {
  const watching = state.continueWatching;
  if (!watching) {
    return `<div class="card empty-note">Episodes you start will show up here.</div>`;
  }
  const anime = findAnime(watching.animeId);
  const progressPercent = Math.round((watching.seconds / VIDEO_LENGTH_SECONDS) * 100);
  return `
    <div class="card clickable continue-card" data-action="resume-watching">
      <div class="cover-placeholder">cover</div>
      <div style="flex:1">
        <div class="anime-title-japanese">${escapeHtml(anime.titleJapanese)} &nbsp;·&nbsp; Episode ${watching.episode}</div>
        <div class="anime-meta">on ${escapeHtml(watching.domain)} &nbsp;|&nbsp; ${formatSeconds(watching.seconds)} watched &nbsp;|&nbsp; Japanese subtitles ready</div>
        <div class="progress-track"><div class="progress-fill" style="width:${progressPercent}%"></div></div>
      </div>
    </div>`;
}

function buildSearchPage(page) {
  return `
    <div class="app-page">
      ${buildSavedSiteResults(page.query)}
      ${buildAnimeResults(page.query)}
      <div class="section-heading">Web</div>
      <div class="card clickable web-search-card" data-action="web-search" data-query="${escapeHtml(page.query)}">
        <span>Search ${escapeHtml(state.settings.searchEngine)} for "${escapeHtml(page.query)}"</span>
        <span class="muted">&#8250;</span>
      </div>
    </div>`;
}

function buildSavedSiteResults(query) {
  const matchingSites = savedSitesMatchingQuery(query);
  const heading = `<div class="section-heading">Your saved sites</div>`;
  if (matchingSites.length === 0) {
    return heading + `<div class="card empty-note">No saved site matches "${escapeHtml(query)}"</div>`;
  }
  const cards = matchingSites.map((site) => `
    <div class="card clickable web-search-card" data-action="open-saved-site" data-domain="${escapeHtml(site.domain)}">
      <span>&#9733; ${escapeHtml(site.name)} <span class="muted">&nbsp;${escapeHtml(site.domain)}</span></span>
      <span class="muted">open &#8250;</span>
    </div>`);
  return heading + cards.join("");
}

function buildAnimeResults(query) {
  const matchingAnime = animeMatchingQuery(query);
  const heading = `<div class="section-heading">Anime</div>`;
  if (matchingAnime.length === 0) {
    return heading + `<div class="card empty-note">No anime called "${escapeHtml(query)}"</div>`;
  }
  return heading + matchingAnime.map(buildAnimeResultCard).join("");
}

function buildAnimeResultCard(anime) {
  return `
    <div class="card anime-card">
      <div class="cover-placeholder">cover</div>
      <div class="details">
        <div class="anime-title-japanese">${escapeHtml(anime.titleJapanese)}</div>
        <div class="anime-meta">${escapeHtml(anime.titleEnglish)}</div>
        <div class="anime-meta">TV &nbsp;|&nbsp; ${anime.episodeCount} episodes &nbsp;|&nbsp; ${escapeHtml(anime.year)}</div>
        ${buildSubtitleStatus(anime)}
      </div>
      <div class="open-buttons">${buildOpenOnSiteButtons(anime)}</div>
    </div>`;
}

function buildSubtitleStatus(anime) {
  if (anime.episodesWithJapaneseSubtitles >= anime.episodeCount) {
    return `<div class="subtitle-status complete">Japanese subtitles: all ${anime.episodeCount} episodes</div>`;
  }
  return `<div class="subtitle-status partial">Japanese subtitles: up to episode ${anime.episodesWithJapaneseSubtitles}</div>`;
}

function buildOpenOnSiteButtons(anime) {
  if (state.savedSites.length === 0) {
    return `<span class="empty-note">Save a site to open this there</span>`;
  }
  return state.savedSites.slice(0, 3).map((site, siteIndex) => `
    <button class="pill-button ${siteIndex === 0 ? "primary" : ""}" data-action="open-anime-on-site"
      data-domain="${escapeHtml(site.domain)}" data-anime-id="${anime.id}">open on ${escapeHtml(site.name)}</button>`).join("");
}

function fakeDomainsForQuery(query) {
  const slug = query.toLowerCase().replace(/[^a-z0-9]/g, "") || "anime";
  return [slug + ".to", slug + "tv.net", "watch" + slug + ".lol", slug + "-hub.cc"];
}

function buildWebResultsPage(page) {
  const results = fakeDomainsForQuery(page.query).map((domain) => `
    <div class="search-result" data-action="open-search-result" data-domain="${escapeHtml(domain)}">
      <div class="search-result-address">https://${escapeHtml(domain)}</div>
      <div class="search-result-title">${escapeHtml(siteNameFromDomain(domain))} - Watch Anime Online</div>
      <div class="search-result-text">Stream the latest anime episodes. Sample result made up by the mockup.</div>
    </div>`);
  return `
    <div class="search-engine-page">
      <div class="search-engine-header">
        <div class="search-engine-name">${escapeHtml(state.settings.searchEngine)}</div>
        <div class="search-engine-box">${escapeHtml(page.query)}</div>
      </div>
      <div class="search-engine-note">Mockup: in the real app, the real ${escapeHtml(state.settings.searchEngine)} results page loads here, as in any browser.</div>
      ${results.join("")}
    </div>`;
}

function buildFakeSiteHeader(domain, query) {
  return `
    <div class="fake-site-header">
      <div class="fake-site-logo">${escapeHtml(siteNameFromDomain(domain))}</div>
      <form class="fake-site-search" data-domain="${escapeHtml(domain)}">
        <input name="query" placeholder="Search anime..." value="${escapeHtml(query || "")}">
        <button type="submit">Search</button>
      </form>
    </div>`;
}

function animeShownOnSite(query) {
  if (!query) {
    return ANIME_CATALOG;
  }
  return animeMatchingQuery(query);
}

function buildSitePage(page) {
  const animeList = animeShownOnSite(page.query);
  const heading = page.query ? `Results for "${escapeHtml(page.query)}"` : "Popular this season";
  const tiles = animeList.map((anime) => `
    <div class="fake-site-tile" data-action="open-site-anime" data-domain="${escapeHtml(page.domain)}" data-anime-id="${anime.id}">
      <div class="fake-site-poster" style="background:${anime.posterColor}">poster</div>
      <div class="fake-site-tile-title">${escapeHtml(anime.titleEnglish)}</div>
    </div>`);
  const emptyNote = animeList.length === 0 ? `<p>No results.</p>` : "";
  return `
    <div class="fake-site">
      ${buildFakeSiteHeader(page.domain, page.query)}
      <div class="fake-site-body">
        <div class="fake-site-ad-slot">ad blocked by the app</div>
        <h3>${heading}</h3>
        ${emptyNote}
        <div class="fake-site-grid">${tiles.join("")}</div>
      </div>
    </div>`;
}

function buildSiteAnimePage(page) {
  const anime = findAnime(page.animeId);
  const episodeButtons = [];
  for (let episode = 1; episode <= anime.episodeCount; episode += 1) {
    episodeButtons.push(`<button class="episode-button" data-action="play-episode" data-domain="${escapeHtml(page.domain)}"
      data-anime-id="${anime.id}" data-episode="${episode}">EP ${episode}</button>`);
  }
  return `
    <div class="fake-site">
      ${buildFakeSiteHeader(page.domain, "")}
      <div class="fake-site-body">
        <div class="fake-site-ad-slot">ad blocked by the app</div>
        <h2>${escapeHtml(anime.titleEnglish)}</h2>
        <div style="color:#8A90A8">${anime.episodeCount} episodes · English subtitles</div>
        <div class="episode-grid">${episodeButtons.join("")}</div>
      </div>
    </div>`;
}

function buildPlayerPage() {
  return `
    <div class="player">
      <div class="video-scene" id="video-scene">
        <div class="video-scene-title">video playing (mockup)</div>
        <div class="speaking-indicator" id="speaking-indicator"></div>
      </div>
      <div class="burned-in-english" id="burned-in-english"></div>
      <div class="subtitle-box size-${state.settings.subtitleSize}" id="subtitle-box"></div>
      <div class="video-controls">
        <button data-action="toggle-play" id="play-button">&#9654;</button>
        <span id="video-time">0:00</span>
        <div class="seek-track" data-action="seek" id="seek-track"><div class="seek-fill" id="seek-fill"></div></div>
        <span>${formatSeconds(VIDEO_LENGTH_SECONDS)}</span>
      </div>
    </div>`;
}

function buildSettingsPage() {
  return `
    <div class="app-page">
      <h1 class="page-heading">Settings</h1>
      ${buildSettingsSelect("Search engine", "searchEngine", ["Google", "DuckDuckGo", "Bing"])}
      ${buildSettingsSelect("Theme", "theme", ["dark", "light"])}
      ${buildSettingsSelect("Subtitle size", "subtitleSize", ["small", "medium", "large"])}
      <div class="settings-row">
        <span>Reset the mockup (saved sites, continue watching)</span>
        <button class="pill-button" data-action="reset-demo">Reset</button>
      </div>
    </div>`;
}

function buildSettingsSelect(labelText, settingKey, choices) {
  const options = choices.map((choice) =>
    `<option value="${choice}" ${state.settings[settingKey] === choice ? "selected" : ""}>${choice}</option>`
  );
  return `
    <div class="settings-row">
      <span>${labelText}</span>
      <select data-setting="${settingKey}">${options.join("")}</select>
    </div>`;
}

function renderDetectionBar() {
  const detectionBar = document.getElementById("detection-bar");
  const player = state.player;
  detectionBar.classList.toggle("hidden", !player);
  if (!player) {
    return;
  }
  const anime = findAnime(player.animeId);
  const hasSubtitles = player.episode <= anime.episodesWithJapaneseSubtitles;
  const subtitleText = hasSubtitles
    ? `<span style="color:var(--good)">Japanese subtitles loaded</span>`
    : `<span style="color:var(--warning)">No Japanese subtitles for this episode yet</span>`;
  detectionBar.innerHTML = `
    <span>Watching: <b>${escapeHtml(anime.titleJapanese)}</b> · Episode ${player.episode}</span>
    ${subtitleText}
    <button class="link-button" data-action="open-detection-editor">change</button>
    <span class="grow"></span>
    <span class="sync-pill" id="sync-pill"></span>
    <button class="small-button" data-action="adjust-offset" data-delta="-0.5">−0.5 s</button>
    <button class="small-button" data-action="adjust-offset" data-delta="0.5">+0.5 s</button>
    <button class="small-button" data-action="resync">auto-sync</button>`;
}

function createPlayerState(domain, animeId, episode, startSeconds) {
  return {
    domain,
    animeId,
    episode,
    currentSeconds: startSeconds,
    isPlaying: true,
    offsetSeconds: 0,
    syncStatus: "syncing",
    secondsListened: 0,
    subtitlesVisible: true,
    ticksSinceSave: 0,
  };
}

function startPlayer(domain, animeId, episode, startSeconds) {
  state.player = createPlayerState(domain, animeId, episode, startSeconds);
  openPage({ type: "player", domain, animeId, episode });
}

function savedSecondsForEpisode(page) {
  const watching = state.continueWatching;
  const isSameEpisode = watching && watching.domain === page.domain && watching.animeId === page.animeId && watching.episode === page.episode;
  return isSameEpisode ? watching.seconds : 0;
}

function ensurePlayerForPage(page) {
  if (page.type === "player" && !state.player) {
    state.player = createPlayerState(page.domain, page.animeId, page.episode, savedSecondsForEpisode(page));
  }
}

function playerHasSubtitles(player) {
  return player.episode <= findAnime(player.animeId).episodesWithJapaneseSubtitles;
}

function tickPlayer() {
  const player = state.player;
  if (!player || !player.isPlaying) {
    return;
  }
  player.currentSeconds += PLAYER_TICK_SECONDS;
  listenForAutoSync(player);
  stopAtEndOfVideo(player);
  saveContinueWatchingEveryFewSeconds(player);
  updatePlayerView();
}

function listenForAutoSync(player) {
  if (player.syncStatus !== "syncing") {
    return;
  }
  player.secondsListened += PLAYER_TICK_SECONDS;
  if (player.secondsListened >= SECONDS_OF_AUDIO_NEEDED_FOR_SYNC) {
    player.offsetSeconds = -SUBTITLE_FILE_DELAY_SECONDS;
    player.syncStatus = "auto";
    showToast("Auto-sync: subtitles moved " + formatOffset(player.offsetSeconds) + " to match the voices");
  }
}

function stopAtEndOfVideo(player) {
  if (player.currentSeconds >= VIDEO_LENGTH_SECONDS) {
    player.currentSeconds = VIDEO_LENGTH_SECONDS;
    player.isPlaying = false;
  }
}

function saveContinueWatchingEveryFewSeconds(player) {
  player.ticksSinceSave += 1;
  if (player.ticksSinceSave >= 20) {
    player.ticksSinceSave = 0;
    saveContinueWatching();
  }
}

function saveContinueWatching() {
  const player = state.player;
  if (!player) {
    return;
  }
  state.continueWatching = {
    domain: player.domain,
    animeId: player.animeId,
    episode: player.episode,
    seconds: player.currentSeconds,
  };
  storeValue("continueWatching", state.continueWatching);
}

function updatePlayerView() {
  const player = state.player;
  if (!player || !document.getElementById("subtitle-box")) {
    return;
  }
  updateVideoControls(player);
  updateVideoScene(player);
  updateSubtitleBox(player);
  updateSyncPill(player);
}

function updateVideoControls(player) {
  document.getElementById("video-time").textContent = formatSeconds(player.currentSeconds);
  document.getElementById("seek-fill").style.width = (player.currentSeconds / VIDEO_LENGTH_SECONDS) * 100 + "%";
  document.getElementById("play-button").innerHTML = player.isPlaying ? "&#10074;&#10074;" : "&#9654;";
}

function updateVideoScene(player) {
  const hue = Math.round((player.currentSeconds * 7) % 360);
  document.getElementById("video-scene").style.background =
    `linear-gradient(135deg, hsl(${hue}, 35%, 28%), hsl(${(hue + 60) % 360}, 35%, 18%))`;
  const spokenIndex = lineIndexSpokenAt(player.currentSeconds);
  const speakingIndicator = document.getElementById("speaking-indicator");
  speakingIndicator.textContent = spokenIndex === null ? "(quiet)" : "character is speaking";
  document.getElementById("burned-in-english").textContent =
    spokenIndex === null ? "" : SUBTITLE_LINES[spokenIndex].burnedInEnglish;
}

function updateSubtitleBox(player) {
  const subtitleBox = document.getElementById("subtitle-box");
  const lineIndex = playerHasSubtitles(player) ? subtitleLineIndexAt(player.currentSeconds, player.offsetSeconds) : null;
  subtitleBox.classList.toggle("hidden", !player.subtitlesVisible);
  subtitleBox.classList.toggle("is-empty", lineIndex === null);
  subtitleBox.textContent = lineIndex === null ? "" : SUBTITLE_LINES[lineIndex].tokens.join("");
  if (player.subtitlesVisible && lineIndex !== null) {
    letPlayTranslateReadLine(lineIndex);
  }
}

function updateSyncPill(player) {
  const syncPill = document.getElementById("sync-pill");
  if (!syncPill) {
    return;
  }
  const pillTexts = {
    syncing: "listening to sync...",
    auto: "auto-synced " + formatOffset(player.offsetSeconds),
    manual: "manual " + formatOffset(player.offsetSeconds),
  };
  syncPill.textContent = pillTexts[player.syncStatus];
  syncPill.classList.toggle("is-syncing", player.syncStatus === "syncing");
}

function letPlayTranslateReadLine(lineIndex) {
  const playTranslate = state.playTranslate;
  if (lineIndex === playTranslate.lineIndex || lineIndex === playTranslate.pendingLineIndex) {
    return;
  }
  playTranslate.pendingLineIndex = lineIndex;
  setTimeout(() => {
    if (playTranslate.pendingLineIndex !== lineIndex) {
      return;
    }
    playTranslate.lineIndex = lineIndex;
    playTranslate.pendingLineIndex = null;
    playTranslate.selectedTokenIndex = null;
    renderBottomScreen();
  }, PLAYTRANSLATE_READ_DELAY_MS);
}

function renderBottomScreen() {
  const bottomScreen = document.getElementById("bottom-screen");
  const playTranslate = state.playTranslate;
  const header = `<div class="stand-in-label"><span>PlayTranslate (stand-in)</span><span>auto mode · capture region: subtitle box</span></div>`;
  if (playTranslate.lineIndex === null) {
    bottomScreen.innerHTML = header + `<div class="waiting-state">Waiting for text in the capture region.<br>Start an episode on the top screen.</div>`;
    return;
  }
  const line = SUBTITLE_LINES[playTranslate.lineIndex];
  bottomScreen.innerHTML = `
    ${header}
    <div class="card">
      <div class="reading-line">${buildTappableTokens(line, playTranslate.selectedTokenIndex)}</div>
    </div>
    <div class="card translation-text">${escapeHtml(line.translation)} &nbsp;(offline translation)</div>
    ${buildWordCard(line, playTranslate.selectedTokenIndex)}`;
}

function buildTappableTokens(line, selectedTokenIndex) {
  return line.tokens.map((token, tokenIndex) => {
    if (!DICTIONARY[token]) {
      return `<span class="word">${escapeHtml(token)}</span>`;
    }
    const selectedClass = tokenIndex === selectedTokenIndex ? "is-selected" : "";
    return `<span class="word has-entry ${selectedClass}" data-action="select-word" data-token-index="${tokenIndex}">${escapeHtml(token)}</span>`;
  }).join("");
}

function buildWordCard(line, selectedTokenIndex) {
  if (selectedTokenIndex === null) {
    return `<div class="card empty-note">Tap a word to look it up.</div>`;
  }
  const entry = DICTIONARY[line.tokens[selectedTokenIndex]];
  const meanings = entry.meanings.map((meaning) => `<li>${escapeHtml(meaning)}</li>`).join("");
  const formNote = entry.form ? `<div class="anime-meta">form here: ${escapeHtml(entry.form)}</div>` : "";
  return `
    <div class="card word-card">
      <div class="word-heading">
        <span class="word-headword">${escapeHtml(entry.headword)}</span>
        <span class="word-reading">${escapeHtml(entry.reading)}</span>
        <span class="level-chip level-${entry.level}">${entry.level}</span>
      </div>
      ${formNote}
      <ol class="word-meanings">${meanings}</ol>
      <div class="word-card-footer">
        <span>sentence + translation go on the card</span>
        <button class="pill-button primary" data-action="add-to-anki">+ Anki</button>
      </div>
    </div>`;
}

function showToast(message) {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.classList.remove("hidden");
  clearTimeout(showToast.hideTimer);
  showToast.hideTimer = setTimeout(() => toast.classList.add("hidden"), 2600);
}

function openDetectionEditor() {
  const player = state.player;
  const animeOptions = ANIME_CATALOG.map((anime) =>
    `<option value="${anime.id}" ${anime.id === player.animeId ? "selected" : ""}>${escapeHtml(anime.titleJapanese)} (${escapeHtml(anime.titleEnglish)})</option>`
  );
  const modal = document.getElementById("modal");
  modal.innerHTML = `
    <div class="modal-card">
      <div class="section-heading" style="margin-top:0">What are you watching?</div>
      <div class="anime-meta">The app guessed from the page. Fix it here and it loads the right subtitles.</div>
      <label>Anime</label>
      <select id="detection-anime">${animeOptions.join("")}</select>
      <label>Episode</label>
      <input id="detection-episode" type="number" min="1" value="${player.episode}">
      <div class="modal-actions">
        <button class="pill-button" data-action="close-modal">Cancel</button>
        <button class="pill-button primary" data-action="apply-detection">Load subtitles</button>
      </div>
    </div>`;
  modal.classList.remove("hidden");
}

function closeModal() {
  document.getElementById("modal").classList.add("hidden");
}

function applyDetection() {
  const player = state.player;
  const anime = findAnime(document.getElementById("detection-anime").value);
  const episode = Math.max(1, Math.min(anime.episodeCount, Number(document.getElementById("detection-episode").value) || 1));
  player.animeId = anime.id;
  player.episode = episode;
  player.syncStatus = "syncing";
  player.secondsListened = 0;
  player.offsetSeconds = 0;
  closeModal();
  renderDetectionBar();
  updatePlayerView();
  showToast("Loaded subtitles for " + anime.titleJapanese + " episode " + episode);
}

function seekToLine(lineIndex) {
  const player = state.player;
  if (!player) {
    return;
  }
  const safeIndex = Math.max(0, Math.min(SUBTITLE_LINES.length - 1, lineIndex));
  player.currentSeconds = subtitleStartSeconds(safeIndex, player.offsetSeconds) + 0.01;
  updatePlayerView();
}

function currentLineIndex() {
  const player = state.player;
  return lastStartedSubtitleLineIndex(player.currentSeconds, player.offsetSeconds);
}

function seekFromClick(event) {
  const player = state.player;
  const track = document.getElementById("seek-track");
  const trackBounds = track.getBoundingClientRect();
  const fraction = (event.clientX - trackBounds.left) / trackBounds.width;
  player.currentSeconds = Math.max(0, Math.min(VIDEO_LENGTH_SECONDS, fraction * VIDEO_LENGTH_SECONDS));
  updatePlayerView();
}

function toggleSavedSite() {
  const domain = state.currentPage.domain;
  if (isSiteSaved(domain)) {
    state.savedSites = state.savedSites.filter((site) => site.domain !== domain);
    showToast("Removed " + domain + " from saved sites");
  } else {
    state.savedSites.push({ domain, name: siteNameFromDomain(domain) });
    showToast("Saved " + domain + ". It's on the home page now.");
  }
  storeValue("savedSites", state.savedSites);
  updateSiteControls(state.currentPage);
}

function applyTheme() {
  document.documentElement.setAttribute("data-theme", state.settings.theme);
}

function changeSetting(settingKey, value) {
  state.settings[settingKey] = value;
  storeValue("settings", state.settings);
  applyTheme();
}

function resetDemo() {
  state.savedSites = [];
  state.continueWatching = null;
  state.history = [];
  state.playTranslate = { lineIndex: null, pendingLineIndex: null, selectedTokenIndex: null };
  storeValue("savedSites", []);
  storeValue("continueWatching", null);
  renderBottomScreen();
  openPage({ type: "home" }, { skipHistory: true });
  showToast("Mockup reset");
}

function handleAddressBarSubmit(text) {
  const trimmedText = text.trim();
  if (!trimmedText) {
    return;
  }
  if (looksLikeAddress(trimmedText)) {
    openPage({ type: "site", domain: domainFromAddress(trimmedText) });
    return;
  }
  openPage({ type: "search", query: trimmedText });
}

function runWhenPlayerIsOpen(action) {
  if (state.player) {
    action(state.player);
  }
}

const actionHandlers = {
  "go-back": () => goBack(),
  "go-home": () => goHome(),
  "open-settings": () => openPage({ type: "settings" }),
  "toggle-saved-site": () => toggleSavedSite(),
  "open-saved-site": (data) => openPage({ type: "site", domain: data.domain }),
  "open-anime-on-site": (data) => openPage({ type: "site", domain: data.domain, query: findAnime(data.animeId).titleEnglish }),
  "web-search": (data) => openPage({ type: "web-results", query: data.query }),
  "open-search-result": (data) => openPage({ type: "site", domain: data.domain }),
  "open-site-anime": (data) => openPage({ type: "site-anime", domain: data.domain, animeId: data.animeId }),
  "play-episode": (data) => startPlayer(data.domain, data.animeId, Number(data.episode), 0),
  "resume-watching": () => {
    const watching = state.continueWatching;
    startPlayer(watching.domain, watching.animeId, watching.episode, watching.seconds);
  },
  "toggle-play": () => runWhenPlayerIsOpen((player) => {
    player.isPlaying = !player.isPlaying;
    updatePlayerView();
  }),
  "previous-line": () => runWhenPlayerIsOpen(() => seekToLine(currentLineIndex() - 1)),
  "replay-line": () => runWhenPlayerIsOpen(() => seekToLine(currentLineIndex())),
  "next-line": () => runWhenPlayerIsOpen(() => seekToLine(currentLineIndex() + 1)),
  "toggle-subtitles": () => runWhenPlayerIsOpen((player) => {
    player.subtitlesVisible = !player.subtitlesVisible;
    updatePlayerView();
    showToast(player.subtitlesVisible ? "Subtitle shown" : "Subtitle hidden (PlayTranslate keeps the last line)");
  }),
  "adjust-offset": (data) => runWhenPlayerIsOpen((player) => {
    player.offsetSeconds += Number(data.delta);
    player.syncStatus = "manual";
    updatePlayerView();
  }),
  "resync": () => runWhenPlayerIsOpen((player) => {
    player.offsetSeconds = 0;
    player.secondsListened = 0;
    player.syncStatus = "syncing";
    updatePlayerView();
  }),
  "open-detection-editor": () => openDetectionEditor(),
  "close-modal": () => closeModal(),
  "apply-detection": () => applyDetection(),
  "select-word": (data) => {
    state.playTranslate.selectedTokenIndex = Number(data.tokenIndex);
    renderBottomScreen();
  },
  "add-to-anki": () => showToast("Card sent to AnkiDroid (mockup)"),
  "reset-demo": () => resetDemo(),
};

function handleClick(event) {
  const actionElement = event.target.closest("[data-action]");
  if (!actionElement) {
    return;
  }
  const actionName = actionElement.dataset.action;
  if (actionName === "seek") {
    seekFromClick(event);
    return;
  }
  actionHandlers[actionName](actionElement.dataset);
}

function handleSubmit(event) {
  event.preventDefault();
  const form = event.target;
  if (form.id === "address-form") {
    handleAddressBarSubmit(document.getElementById("address-bar").value);
    return;
  }
  if (form.classList.contains("fake-site-search")) {
    openPage({ type: "site", domain: form.dataset.domain, query: form.elements.query.value.trim() });
  }
}

function handleSettingChange(event) {
  const settingKey = event.target.dataset.setting;
  if (settingKey) {
    changeSetting(settingKey, event.target.value);
  }
}

const keyboardActions = {
  " ": "toggle-play",
  q: "previous-line",
  e: "next-line",
  r: "replay-line",
  h: "toggle-subtitles",
};

function handleKeyDown(event) {
  const typingInField = ["INPUT", "SELECT", "TEXTAREA"].includes(event.target.tagName);
  const actionName = keyboardActions[event.key.toLowerCase()];
  if (typingInField || !actionName) {
    return;
  }
  event.preventDefault();
  actionHandlers[actionName]({});
}

const FULL_LAYOUT_WIDTH_PIXELS = 1380;
const FULL_LAYOUT_HEIGHT_PIXELS = 1180;

function fitMockupToWindow() {
  const widthScale = window.innerWidth / FULL_LAYOUT_WIDTH_PIXELS;
  const heightScale = window.innerHeight / FULL_LAYOUT_HEIGHT_PIXELS;
  document.body.style.zoom = Math.min(1, widthScale, heightScale);
}

function startMockup() {
  applyTheme();
  fitMockupToWindow();
  window.addEventListener("resize", fitMockupToWindow);
  document.addEventListener("click", handleClick);
  document.addEventListener("submit", handleSubmit);
  document.addEventListener("change", handleSettingChange);
  document.addEventListener("keydown", handleKeyDown);
  openPage({ type: "home" }, { skipHistory: true });
  renderBottomScreen();
  setInterval(tickPlayer, PLAYER_TICK_SECONDS * 1000);
}

startMockup();
