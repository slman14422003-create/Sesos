(function () {
  if (window.top !== window) return;
  let last = "";
  function report() {
    const u = location.href;
    if (u !== last) {
      last = u;
      try { browser.runtime.sendMessage({ type: "url", url: u, ua: navigator.userAgent }); } catch (e) {}
    }
  }
  report();
  setInterval(report, 800);
})();
