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

  let t = null;
  async function syncAlarms() {
    try {
      const s = settings();
      await PillAlarm.schedule({ items: buildItems(), lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate, snoozeMinutes: s.snooze });
    } catch (e) { console.warn("alarm sync failed", e); }
  }
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

    try { await PillAlarm.requestPermissions(); } catch (e) {}
    syncAlarms();

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
