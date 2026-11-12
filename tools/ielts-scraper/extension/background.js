// The crawler needs DOMParser, which a service worker does not have, so it runs in an extension tab.
chrome.action.onClicked.addListener(() => {
  chrome.tabs.create({ url: chrome.runtime.getURL("crawler.html") });
});
