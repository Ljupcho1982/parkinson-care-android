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
    const ok = st.exactAllowed && st.notificationsAllowed;
    const next = st.nextAt ? new Date(st.nextAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—";
    let msg;
    if (!st.notificationsAllowed) msg = "⚠️ Notifications are OFF — enable them in Android settings so alarms can show.";
    else if (!st.exactAllowed) msg = "⚠️ Exact alarms are OFF — tap here to allow them.";
    else if (!st.scheduledCount) msg = "ℹ️ No pills scheduled yet. Add a medication in “My Pills”.";
    else msg = "✅ " + st.scheduledCount + " alarm(s) set · next at " + next;
    bar.textContent = msg;
    bar.style.background = ok && st.scheduledCount ? "rgba(46,204,113,.15)" : "rgba(244,183,64,.18)";
    bar.style.color = ok && st.scheduledCount ? "#1c7d46" : "#8a6500";
    bar.onclick = (!st.exactAllowed) ? () => { try { PillAlarm.ensurePermissions(); } catch (e) {} } : null;
    bar.style.cursor = (!st.exactAllowed) ? "pointer" : "default";
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

    // make the "Test the alarm" button trigger the REAL native full-screen alarm
    const tb = document.getElementById("testAlarm");
    if (tb) {
      tb.textContent = "🔔 Test the real alarm (rings full-screen + voice)";
      tb.onclick = async () => {
        const s = settings();
        try { await PillAlarm.test({ name: "Levodopa/Carbidopa", dose: "100/25 mg · 1 tablet", lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate }); }
        catch (e) { console.warn("native test failed", e); }
      };
    }
  });

  // keep alarms fresh when app returns to foreground
  document.addEventListener("visibilitychange", () => { if (!document.hidden) scheduleSoon(); });
})();
