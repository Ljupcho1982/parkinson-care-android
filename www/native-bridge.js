/* Connects the web UI to the native Android alarm engine (PillAlarm plugin).
   Runs only inside the native app; on the web it does nothing. */
(function () {
  const Cap = window.Capacitor;
  if (!Cap || !Cap.isNativePlatform || !Cap.isNativePlatform()) return;

  const PillAlarm = Cap.registerPlugin("PillAlarm");
  document.documentElement.classList.add("native");

  // Disable the web-only alarm/voice engine — the native AlarmManager handles alarms now,
  // so we don't get a duplicate in-app overlay on top of the real full-screen alarm.
  try { window.checkDue = function () {}; window.showAlarm = function () {}; } catch (e) {}

  function buildItems() {
    let meds = [];
    try { meds = JSON.parse(localStorage.getItem("pk_meds")) || []; } catch (e) {}
    const items = [];
    meds.forEach(m => (m.times || []).forEach(t => {
      const [h, mi] = String(t).split(":").map(Number);
      if (Number.isFinite(h) && Number.isFinite(mi)) {
        items.push({ id: (m.id || "") + "_" + t, name: m.name || "Medication", dose: m.dose || "", hour: h, minute: mi });
      }
    }));
    return items;
  }
  function settings() {
    let s = {};
    try { s = JSON.parse(localStorage.getItem("pk_settings")) || {}; } catch (e) {}
    return { lang: s.lang || "auto", voice: s.voice !== false, sound: s.sound !== false, vibrate: s.vibrate !== false, snooze: Number(s.snooze) || 10 };
  }

  function renderStatus(st) {
    if (!st) return;
    let bar = document.getElementById("pkStatus");
    if (!bar) {
      bar = document.createElement("div");
      bar.id = "pkStatus";
      bar.style.cssText = "margin:0 0 14px;padding:12px 14px;border-radius:12px;font-size:.9rem;font-weight:600;line-height:1.4";
      const wrap = document.querySelector(".wrap");
      if (wrap) wrap.insertBefore(bar, wrap.firstChild);
    }
    const fmt = ms => ms ? new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—";
    const battery = st.batteryUnrestricted !== false;
    const overlay = st.overlayAllowed !== false;
    const ok = st.exactAllowed && st.notificationsAllowed && battery && overlay;
    const needFix = !st.exactAllowed || !st.notificationsAllowed || !battery || !overlay;
    let head;
    if (!st.notificationsAllowed) head = "⚠️ Notifications OFF — tap to fix";
    else if (!overlay) head = "⚠️ “Appear on top” OFF — tap to allow (lets the alarm pop up)";
    else if (!st.exactAllowed) head = "⚠️ Exact alarms OFF — tap to fix";
    else if (!battery) head = "⚠️ Battery optimization ON — tap to allow (keeps alarms alive)";
    else if (!st.scheduledCount) head = "ℹ️ No pills scheduled — add one in “My Pills”";
    else head = "✅ " + st.scheduledCount + " alarm(s) set · next at " + fmt(st.nextAt);

    const lastFired = st.lastFiredAt
      ? "Last alarm fired: " + fmt(st.lastFiredAt) + (st.lastFiredName ? " (" + st.lastFiredName + ")" : "")
      : "Last alarm fired: never yet";
    const perms = "Notifications: " + (st.notificationsAllowed ? "on" : "OFF")
      + " · Appear-on-top: " + (overlay ? "on" : "OFF")
      + " · Exact alarms: " + (st.exactAllowed ? "on" : "OFF")
      + " · Battery: " + (battery ? "unrestricted" : "RESTRICTED");

    bar.innerHTML = "<div>" + head + "</div>" +
      "<div style='font-weight:400;opacity:.85;margin-top:4px;font-size:.82rem'>" + perms + "<br>" + lastFired + "</div>";
    bar.style.background = ok && st.scheduledCount ? "rgba(46,204,113,.15)" : "rgba(244,183,64,.18)";
    bar.style.color = ok && st.scheduledCount ? "#1c7d46" : "#8a6500";
    bar.onclick = needFix ? () => { try { PillAlarm.ensurePermissions(); } catch (e) {} } : null;
    bar.style.cursor = needFix ? "pointer" : "default";
  }

  let t = null;
  async function syncAlarms() {
    try {
      const s = settings();
      const st = await PillAlarm.schedule({ items: buildItems(), lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate, snoozeMinutes: s.snooze });
      renderStatus(st);
    } catch (e) { console.warn("alarm sync failed", e); }
  }
  async function refreshStatus() { try { renderStatus(await PillAlarm.getStatus()); } catch (e) {} }
  function scheduleSoon() { clearTimeout(t); t = setTimeout(syncAlarms, 700); }

  // re-sync whenever the schedule or settings change
  const origSet = localStorage.setItem.bind(localStorage);
  localStorage.setItem = function (k, v) {
    origSet(k, v);
    if (k === "pk_meds" || k === "pk_settings") scheduleSoon();
  };

  window.addEventListener("load", async () => {
    // hide PWA-only banners inside the native app
    ["installBanner", "notifBanner"].forEach(id => { const el = document.getElementById(id); if (el) el.style.display = "none"; });

    try { await PillAlarm.ensurePermissions(); } catch (e) {}
    await syncAlarms();
    refreshStatus();

    // "Test now" — fires immediately while the app is open
    const tb = document.getElementById("testAlarm");
    if (tb) {
      tb.textContent = "🔔 Test now (app open)";
      tb.onclick = async () => {
        const s = settings();
        try { await PillAlarm.test({ name: "Levodopa/Carbidopa", dose: "100/25 mg · 1 tablet", lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate }); }
        catch (e) { console.warn("native test failed", e); }
      };

      // "Test in 15s then lock" — fires via the REAL scheduled path so we can verify closed/locked behaviour
      const b2 = document.createElement("button");
      b2.className = tb.className;
      b2.style.marginTop = "8px";
      b2.textContent = "🔒 Test in 15 sec — then LOCK the phone";
      b2.onclick = async () => {
        const s = settings();
        try {
          await PillAlarm.scheduleTest({ seconds: 15, lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate });
          alert("Now LOCK your phone (press the power button). The alarm should ring in about 15 seconds.");
        } catch (e) { console.warn("scheduleTest failed", e); }
      };
      tb.parentNode.insertBefore(b2, tb.nextSibling);
    }

    // keep the status line current (so 'Last alarm fired' updates after a test)
    setInterval(refreshStatus, 4000);
  });

  // keep alarms fresh when app returns to foreground
  document.addEventListener("visibilitychange", () => { if (!document.hidden) scheduleSoon(); });
})();
