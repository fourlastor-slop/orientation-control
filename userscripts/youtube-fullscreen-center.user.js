// ==UserScript==
// @name         YouTube: centre fullscreen video under a rotation lock
// @namespace    io.github.fourlastor.orientation
// @version      1.0
// @description  Centres the video when YouTube goes fullscreen but the screen stays in portrait.
// @match        https://m.youtube.com/*
// @match        https://www.youtube.com/*
// @run-at       document-start
// @grant        none
// @downloadURL  https://raw.githubusercontent.com/fourlastor-slop/orientation-control/main/userscripts/youtube-fullscreen-center.user.js
// @updateURL    https://raw.githubusercontent.com/fourlastor-slop/orientation-control/main/userscripts/youtube-fullscreen-center.user.js
// ==/UserScript==

// Going fullscreen, YouTube's player keeps the video where the page's player was (full width, at the
// top) and only places it again on a resize that comes once it's fullscreen. The rotation to landscape
// that the browser asks for used to provide that resize; with the orientation locked nothing does, so
// send one. The resizes from going fullscreen itself all arrive too early to count.
document.addEventListener('fullscreenchange', () => {
  if (document.fullscreenElement) {
    setTimeout(() => window.dispatchEvent(new Event('resize')), 100);
  }
});
