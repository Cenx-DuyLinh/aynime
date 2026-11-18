const ANILIST_ADDRESS = "https://graphql.anilist.co";
const ANILIST_QUERY =
  "query ($search: String) { Page(perPage: 8) { media(search: $search, type: ANIME) {" +
  " id episodes seasonYear format status title { romaji english native } coverImage { medium } } } }";

const searchQuery = new URLSearchParams(location.search).get("q") || "";

function buildSavedSiteResults(savedSites) {
  const lowerQuery = searchQuery.toLowerCase();
  const matchingSites = savedSites.filter(function (site) {
    return site.domain.includes(lowerQuery) || site.name.toLowerCase().includes(lowerQuery);
  });
  if (matchingSites.length === 0) {
    return '<div class="card empty-note">No saved site matches "' + escapeHtml(searchQuery) + '"</div>';
  }
  return matchingSites.map(function (site) {
    return '<div class="card clickable row-card" data-open-domain="' + escapeHtml(site.domain) + '">' +
      '<span>★ ' + escapeHtml(site.name) + ' <span class="muted">' + escapeHtml(site.domain) + '</span></span>' +
      '<span class="muted">open ›</span></div>';
  }).join("");
}

function animeTitles(anime) {
  return [anime.title.romaji, anime.title.english, anime.title.native].filter(Boolean);
}

function buildOpenButtons(anime, animeIndex, savedSites) {
  if (savedSites.length === 0) {
    return '<span class="empty-note">Save a site to open this there</span>';
  }
  return savedSites.slice(0, 3).map(function (site, siteIndex) {
    const primaryClass = siteIndex === 0 ? " primary" : "";
    return '<button class="pill-button' + primaryClass + '" data-anime-index="' + animeIndex +
      '" data-site-domain="' + escapeHtml(site.domain) + '">open on ' + escapeHtml(site.name) + '</button>';
  }).join("");
}

function buildAnimeCard(anime, animeIndex, savedSites) {
  const nativeTitle = anime.title.native || anime.title.romaji;
  const otherTitle = anime.title.english || anime.title.romaji || "";
  const facts = [anime.format, anime.episodes ? anime.episodes + " episodes" : null, anime.seasonYear]
    .filter(Boolean).join(" · ");
  const cover = anime.coverImage && anime.coverImage.medium
    ? '<img class="cover" src="' + escapeHtml(anime.coverImage.medium) + '">'
    : '<div class="cover"></div>';
  return '<div class="card anime-card">' + cover +
    '<div class="details">' +
    '<div class="anime-title-native">' + escapeHtml(nativeTitle) + '</div>' +
    '<div class="anime-meta">' + escapeHtml(otherTitle) + '</div>' +
    '<div class="anime-meta">' + escapeHtml(facts) + '</div>' +
    '<div class="subtitle-status" id="subtitle-status-' + animeIndex + '">Checking Japanese subtitles…</div>' +
    '</div>' +
    '<div class="open-buttons">' + buildOpenButtons(anime, animeIndex, savedSites) + '</div></div>';
}

function showSubtitleStatus(anime, animeIndex) {
  appBridge.ask({ name: "subtitleStatus", titles: animeTitles(anime) }).then(function (answer) {
    const statusElement = document.getElementById("subtitle-status-" + animeIndex);
    if (!statusElement) {
      return;
    }
    if (answer.folderName) {
      statusElement.className = "subtitle-status found";
      statusElement.textContent = "Japanese subtitles: " + answer.episodeFileCount + " episodes (" + answer.folderName + ")";
    } else {
      statusElement.className = "subtitle-status missing";
      statusElement.textContent = "Japanese subtitles: not found automatically";
    }
  });
}

function searchAniList() {
  return fetch(ANILIST_ADDRESS, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Accept": "application/json" },
    body: JSON.stringify({ query: ANILIST_QUERY, variables: { search: searchQuery } })
  }).then(function (response) {
    return response.json();
  }).then(function (result) {
    return result.data.Page.media;
  });
}

function attachOpenButtons(animeList) {
  document.querySelectorAll("[data-site-domain]").forEach(function (button) {
    button.addEventListener("click", function () {
      const anime = animeList[Number(button.dataset.animeIndex)];
      appBridge.tell({
        name: "openAnimeOnSite",
        domain: button.dataset.siteDomain,
        query: anime.title.english || anime.title.romaji,
        titles: animeTitles(anime)
      });
    });
  });
  document.querySelectorAll("[data-open-domain]").forEach(function (card) {
    card.addEventListener("click", function () {
      appBridge.tell({ name: "openSite", domain: card.dataset.openDomain });
    });
  });
}

function showAnimeResults(animeList, savedSites) {
  const resultsElement = document.getElementById("anime-results");
  if (animeList.length === 0) {
    resultsElement.innerHTML = '<div class="card empty-note">No anime called "' + escapeHtml(searchQuery) + '"</div>';
    return;
  }
  resultsElement.innerHTML = animeList.map(function (anime, index) {
    return buildAnimeCard(anime, index, savedSites);
  }).join("");
  animeList.forEach(showSubtitleStatus);
}

function showSearch() {
  document.getElementById("google-search-text").textContent = 'Search Google for "' + searchQuery + '"';
  document.getElementById("google-search").addEventListener("click", function () {
    appBridge.tell({ name: "searchGoogle", query: searchQuery });
  });
  appBridge.ask({ name: "getHomeData" }).then(function (data) {
    const savedSites = data.savedSites || [];
    document.getElementById("saved-site-results").innerHTML = buildSavedSiteResults(savedSites);
    searchAniList().then(function (animeList) {
      showAnimeResults(animeList, savedSites);
      attachOpenButtons(animeList);
    }).catch(function () {
      document.getElementById("anime-results").innerHTML = '<div class="card empty-note">Anime search is offline right now.</div>';
      attachOpenButtons([]);
    });
  });
}

showSearch();
