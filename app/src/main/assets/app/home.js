const LONG_PRESS_MS = 600;

function buildSavedSites(savedSites) {
  if (savedSites.length === 0) {
    return '<div class="card empty-note">Nothing saved yet. Open a site and tap ☆ in the top bar to keep it here.</div>';
  }
  const tiles = savedSites.map(function (site) {
    return '<div class="card clickable saved-site-tile" data-domain="' + escapeHtml(site.domain) + '">' +
      '<div class="site-badge">' + escapeHtml(site.name.charAt(0).toUpperCase()) + '</div>' +
      '<div>' + escapeHtml(site.name) + '</div></div>';
  });
  return '<div class="saved-sites-grid">' + tiles.join("") + '</div>';
}

function buildContinueWatching(watching) {
  if (!watching) {
    return '<div class="card empty-note">Episodes you start will show up here.</div>';
  }
  const subtitleNote = watching.subtitleLabel ? " · " + escapeHtml(watching.subtitleLabel) : "";
  return '<div class="card clickable" id="resume-card">' +
    '<div><b>' + escapeHtml(watching.title) + '</b></div>' +
    '<div class="anime-meta">' + formatSeconds(watching.seconds) + ' watched' + subtitleNote + '</div></div>';
}

function siteName(domain) {
  return domain.split(".")[0];
}

function episodeText(episode) {
  return episode == null ? "Episode ?" : "Ep " + episode;
}

function timeAgo(watchedAtMs) {
  const minutes = Math.floor((Date.now() - watchedAtMs) / 60000);
  if (minutes < 1) {
    return "just now";
  }
  if (minutes < 60) {
    return minutes + " min ago";
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return hours + " h ago";
  }
  const days = Math.floor(hours / 24);
  return days === 1 ? "yesterday" : days + " days ago";
}

function buildSiteRow(anime, site) {
  return '<div class="watched-site-row" data-anime="' + escapeHtml(anime.name) + '" data-site="' + escapeHtml(site.domain) + '">' +
    '<div><b>' + escapeHtml(siteName(site.domain)) + '</b> <span class="muted">' + escapeHtml(site.domain) + '</span>' +
    '<div class="anime-meta">' + episodeText(site.episode) + ' · ' + formatSeconds(site.seconds) + ' watched · ' +
    timeAgo(site.watchedAtMs) + '</div></div>' +
    '<button class="pill-button primary">continue ›</button></div>';
}

function buildAnimeEntry(anime, animeIndex) {
  const latest = anime.sites[0];
  const siteCount = anime.sites.length === 1 ? "1 site" : anime.sites.length + " sites";
  return '<div class="card clickable watched-anime" data-anime-index="' + animeIndex + '">' +
    '<div class="anime-card">' +
    buildCover(anime) +
    '<div class="details">' +
    '<div class="anime-title-native">' + escapeHtml(anime.shownName || anime.name) + '</div>' +
    '<div class="anime-meta">' + episodeText(latest.episode) + ' on ' + escapeHtml(siteName(latest.domain)) +
    ' · ' + timeAgo(latest.watchedAtMs) + '</div>' +
    '<div class="anime-meta">' + siteCount + ' · tap to choose</div>' +
    (anime.isLookedUp ? '' : '<div class="anime-meta not-matched">Not matched on AniList yet · long-press to choose</div>') +
    '</div></div>' +
    '<div class="watched-sites" hidden>' + anime.sites.map(function (site) { return buildSiteRow(anime, site); }).join("") + '</div>' +
    '</div>';
}

function buildAnimeList(animeList) {
  if (animeList.length === 0) {
    return '<div class="card empty-note">Anime you watch will be listed here, with every site you watched them on.</div>';
  }
  const hint = '<div class="empty-note">Long-press an anime to fix its name and cover, or remove it.</div>';
  return '<div class="anime-list-grid">' + animeList.map(buildAnimeEntry).join("") + '</div>' + hint;
}

function buildCover(anime) {
  if (!anime.coverUrl) {
    const initial = (anime.shownName || anime.name).charAt(0).toUpperCase();
    return '<div class="cover cover-initial">' + escapeHtml(initial) + '</div>';
  }
  return '<img class="cover" src="' + escapeHtml(anime.coverUrl) + '" alt="">';
}

function whenLongPressed(element, action) {
  let pressTimer = null;
  let wasLongPress = false;
  element.addEventListener("touchstart", function () {
    wasLongPress = false;
    pressTimer = setTimeout(function () {
      wasLongPress = true;
      action();
    }, LONG_PRESS_MS);
  }, { passive: true });
  ["touchend", "touchmove", "touchcancel"].forEach(function (eventName) {
    element.addEventListener(eventName, function () { clearTimeout(pressTimer); }, { passive: true });
  });
  element.addEventListener("contextmenu", function (event) { event.preventDefault(); });
  return function () { return wasLongPress; };
}

function attachAnimeListActions(animeList) {
  document.querySelectorAll(".watched-anime").forEach(function (card) {
    const anime = animeList[Number(card.dataset.animeIndex)];
    const isLongPress = whenLongPressed(card, function () {
      appBridge.tell({ name: "animeOptions", anime: anime.name, shownName: anime.shownName || anime.name });
    });
    card.addEventListener("click", function (event) {
      if (isLongPress()) {
        return;
      }
      const siteRow = event.target.closest(".watched-site-row");
      if (siteRow) {
        appBridge.tell({ name: "resumeAnime", anime: siteRow.dataset.anime, domain: siteRow.dataset.site });
        return;
      }
      const siteList = card.querySelector(".watched-sites");
      siteList.hidden = !siteList.hidden;
    });
  });
}

function attachHomeActions() {
  document.querySelectorAll("[data-domain]").forEach(function (tile) {
    tile.addEventListener("click", function () {
      appBridge.tell({ name: "openSite", domain: tile.dataset.domain });
    });
    tile.addEventListener("contextmenu", function (event) {
      event.preventDefault();
      appBridge.tell({ name: "editSite", domain: tile.dataset.domain });
    });
  });
  const resumeCard = document.getElementById("resume-card");
  if (resumeCard) {
    resumeCard.addEventListener("click", function () {
      appBridge.tell({ name: "resumeWatching" });
    });
  }
}

function showHome() {
  appBridge.ask({ name: "getHomeData" }).then(function (data) {
    const savedSites = data.savedSites || [];
    const animeList = data.animeList || [];
    const hint = savedSites.length > 0 ? '<div class="empty-note">Long-press a site to change its address or remove it.</div>' : "";
    document.getElementById("saved-sites").innerHTML = buildSavedSites(savedSites) + hint;
    document.getElementById("continue-watching").innerHTML = buildContinueWatching(data.continueWatching);
    document.getElementById("anime-list").innerHTML = buildAnimeList(animeList);
    attachHomeActions();
    attachAnimeListActions(animeList);
  });
}

window.addEventListener("pageshow", showHome);
