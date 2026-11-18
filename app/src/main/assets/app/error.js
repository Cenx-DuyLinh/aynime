const errorParameters = new URLSearchParams(location.search);
const failedUrl = errorParameters.get("url") || "";
const parsedFailedUrl = parseWebAddress(failedUrl);
const failedDomain = parsedFailedUrl ? parsedFailedUrl.hostname.replace(/^www\./, "") : "";
const failedSiteName = failedDomain.split(".")[0];

// Only an outside http(s) address may be retried; anything else (javascript:, data:, internal pages) is refused.
function parseWebAddress(text) {
  try {
    const address = new URL(text);
    const isWeb = address.protocol === "http:" || address.protocol === "https:";
    return isWeb && address.hostname && address.hostname !== location.hostname ? address : null;
  } catch (error) {
    return null;
  }
}

function showErrorPage() {
  document.getElementById("site-name").textContent = failedDomain;
  document.getElementById("reason").textContent = "Reason: " + (errorParameters.get("reason") || "unknown");
  document.getElementById("search-google-text").textContent = 'Search Google for "' + failedSiteName + '" (find its new address)';
  document.getElementById("try-again").addEventListener("click", function () {
    if (parsedFailedUrl) {
      location.replace(parsedFailedUrl.href);
    }
  });
  document.getElementById("search-google").addEventListener("click", function () {
    appBridge.tell({ name: "searchGoogle", query: failedSiteName });
  });
  const editCard = document.getElementById("edit-site");
  editCard.style.display = "none";
  editCard.addEventListener("click", function () {
    appBridge.tell({ name: "editSite", domain: failedDomain });
  });
  appBridge.ask({ name: "getHomeData" }).then(function (data) {
    const isSaved = (data.savedSites || []).some(function (site) { return site.domain === failedDomain; });
    editCard.style.display = isSaved ? "flex" : "none";
  });
}

showErrorPage();
