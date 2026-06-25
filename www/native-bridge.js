/* Connects the web UI to the native Android alarm engine (PillAlarm plugin).
   Waits for the Capacitor runtime to be ready before doing anything. */
(function () {
  var tries = 0;

  function boot() {
    var Cap = window.Capacitor;
    if (!Cap || !Cap.isNativePlatform || !Cap.registerPlugin) {
      if (tries++ < 100) setTimeout(boot, 100); // wait up to ~10s for Capacitor to inject
      return;
    }
    if (!Cap.isNativePlatform()) return; // running as a plain web page — do nothing
    init(Cap);
  }

  function init(Cap) {
    var PillAlarm = Cap.registerPlugin("PillAlarm");
    document.documentElement.classList.add("native");

    // Disable the web-only alarm engine — native AlarmManager handles alarms now.
    try { window.checkDue = function () {}; window.showAlarm = function () {}; } catch (e) {}

    function buildItems() {
      var meds = [];
      try { meds = JSON.parse(localStorage.getItem("pk_meds")) || []; } catch (e) {}
      var items = [];
      meds.forEach(function (m) {
        (m.times || []).forEach(function (tm) {
          var p = String(tm).split(":");
          var h = Number(p[0]), mi = Number(p[1]);
          if (isFinite(h) && isFinite(mi)) {
            items.push({ id: (m.id || "") + "_" + tm, name: m.name || "Medication", dose: m.dose || "", hour: h, minute: mi });
          }
        });
      });
      return items;
    }
    function settings() {
      var s = {};
      try { s = JSON.parse(localStorage.getItem("pk_settings")) || {}; } catch (e) {}
      return { lang: s.lang || "auto", voice: s.voice !== false, sound: s.sound !== false, vibrate: s.vibrate !== false, snooze: Number(s.snooze) || 10 };
    }

    function renderStatus(st) {
      if (!st) return;
      var bar = document.getElementById("pkStatus");
      if (!bar) {
        bar = document.createElement("div");
        bar.id = "pkStatus";
        bar.style.cssText = "margin:0 0 14px;padding:12px 14px;border-radius:12px;font-size:.9rem;font-weight:600;line-height:1.4";
        var wrap = document.querySelector(".wrap");
        if (wrap) wrap.insertBefore(bar, wrap.firstChild);
      }
      var fmt = function (ms) { return ms ? new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—"; };
      var battery = st.batteryUnrestricted !== false;
      var overlay = st.overlayAllowed !== false;
      var ok = st.exactAllowed && st.notificationsAllowed && battery && overlay;
      var needFix = !st.exactAllowed || !st.notificationsAllowed || !battery || !overlay;
      var head;
      if (!st.notificationsAllowed) head = "⚠️ Notifications OFF — tap to fix";
      else if (!overlay) head = "⚠️ “Appear on top” OFF — tap to allow";
      else if (!st.exactAllowed) head = "⚠️ Exact alarms OFF — tap to fix";
      else if (!battery) head = "⚠️ Battery optimization ON — tap to allow";
      else if (!st.scheduledCount) head = "ℹ️ No pills scheduled — add one in “My Pills”";
      else head = "✅ " + st.scheduledCount + " alarm(s) set · next at " + fmt(st.nextAt);
      var lastFired = st.lastFiredAt ? "Last alarm fired: " + fmt(st.lastFiredAt) + (st.lastFiredName ? " (" + st.lastFiredName + ")" : "") : "Last alarm fired: never yet";
      var perms = "Notifications: " + (st.notificationsAllowed ? "on" : "OFF")
        + " · Appear-on-top: " + (overlay ? "on" : "OFF")
        + " · Exact: " + (st.exactAllowed ? "on" : "OFF")
        + " · Battery: " + (battery ? "ok" : "RESTRICTED");
      bar.innerHTML = "<div>" + head + "</div><div style='font-weight:400;opacity:.85;margin-top:4px;font-size:.82rem'>" + perms + "<br>" + lastFired + "</div>";
      bar.style.background = ok && st.scheduledCount ? "rgba(46,204,113,.15)" : "rgba(244,183,64,.18)";
      bar.style.color = ok && st.scheduledCount ? "#1c7d46" : "#8a6500";
      bar.onclick = needFix ? function () { try { PillAlarm.ensurePermissions(); } catch (e) {} } : null;
      bar.style.cursor = needFix ? "pointer" : "default";
    }

    var t = null;
    function syncAlarms() {
      var s = settings();
      PillAlarm.schedule({ items: buildItems(), lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate, snoozeMinutes: s.snooze })
        .then(renderStatus).catch(function (e) { console.warn("alarm sync failed", e); });
    }
    function refreshStatus() { PillAlarm.getStatus().then(renderStatus).catch(function () {}); }
    function scheduleSoon() { clearTimeout(t); t = setTimeout(syncAlarms, 700); }

    var origSet = localStorage.setItem.bind(localStorage);
    localStorage.setItem = function (k, v) { origSet(k, v); if (k === "pk_meds" || k === "pk_settings") scheduleSoon(); };

    function setup() {
      ["installBanner", "notifBanner"].forEach(function (id) { var el = document.getElementById(id); if (el) el.style.display = "none"; });

      PillAlarm.ensurePermissions().catch(function () {});
      syncAlarms();
      refreshStatus();

      var tb = document.getElementById("testAlarm");
      if (tb) {
        tb.textContent = "🔔 Test now (app open)";
        tb.onclick = function () {
          var s = settings();
          PillAlarm.test({ name: "Levodopa/Carbidopa", dose: "100/25 mg · 1 tablet", lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate }).catch(function () {});
        };
        var b2 = document.createElement("button");
        b2.className = tb.className;
        b2.style.marginTop = "8px";
        b2.textContent = "🔒 Test in 15 sec — then LOCK the phone";
        b2.onclick = function () {
          var s = settings();
          PillAlarm.scheduleTest({ seconds: 15, lang: s.lang, voice: s.voice, sound: s.sound, vibrate: s.vibrate })
            .then(function () { alert("Now press HOME or lock the phone. The alarm fires in ~15 seconds."); })
            .catch(function () {});
        };
        tb.parentNode.insertBefore(b2, tb.nextSibling);
      }

      setInterval(refreshStatus, 4000);
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", setup);
    else setup();

    document.addEventListener("visibilitychange", function () { if (!document.hidden) scheduleSoon(); });
  }

  boot();
})();
