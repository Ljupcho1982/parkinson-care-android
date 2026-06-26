# Google Play — upload guide & store listing

## Files
- **App bundle to upload:** `ParkinsonCare.aab` (from the latest GitHub release) — this is the signed `.aab` Google Play needs.
- Download: https://github.com/Ljupcho1982/parkinson-care-android/releases/download/latest/ParkinsonCare.aab
- **Privacy policy URL:** https://ljupcho1982.github.io/parkinson-care/privacy.html

## App details
- **App name:** Parkinson Care
- **Package / Application ID:** `com.ljupcho.parkinsoncare`
- **Category:** Medical
- **Default language:** English (United States) — can add Macedonian
- **Free / no ads / no in-app purchases**
- **Contains ads:** No
- **Signing:** Use **Google Play App Signing** (recommended). The upload key is your `upload-keystore.jks` (kept locally + as GitHub secrets). Keep a backup of that file.

---

## Store listing text (English)

**Short description (≤80 chars):**
Medication reminders with full-screen voice alarms, for people with Parkinson's.

**Full description:**
Parkinson Care is a simple, private medication reminder and well-being tracker designed for people living with Parkinson's disease — and the people who care for them.

For Parkinson's, dose timing is everything. Parkinson Care makes sure you never miss a dose:

⏰ Full-screen voice alarm — when a dose is due, your phone shows the pill name in large letters, rings, and speaks it out loud, even when the phone is locked.
✅ One-tap dose logging — mark a pill as taken with a single tap; see what's due, overdue, and your daily adherence.
📝 Daily well-being diary — track tremor, stiffness, energy, sleep and "off" episodes, with notes for your doctor.
📋 History & export — review your records and export them (JSON/CSV) to share with your neurologist.
🔒 100% private — all your data stays on your device. No account, no servers, no tracking, no ads.

Parkinson Care helps you track and remember — it does not give medical advice and is not a medical device. Always follow your doctor's or neurologist's instructions.

---

## Store listing text (Macedonian) — optional second language

**Краток опис:**
Потсетници за лекови со гласовен аларм на цел екран, за лица со Паркинсон.

**Целосен опис:**
Parkinson Care е едноставна и приватна апликација за потсетување на лекови и следење на самочувството, наменета за лица со Паркинсонова болест и оние што се грижат за нив.

⏰ Гласовен аларм на цел екран — кога е време за лек, телефонот покажува големи букви, ѕвони и кажува на глас, дури и кога е заклучен.
✅ Евиденција со едно копче — означи земен лек со едно допирање; види што е на ред и колку редовно ги земаш.
📝 Дневник за самочувство — тремор, вкочанетост, енергија, сон и „off" периоди, со белешки за лекарот.
📋 Историја и извоз — прегледај и извези (JSON/CSV) за твојот невролог.
🔒 100% приватно — сите податоци остануваат на твојот телефон. Без сметка, без сервери, без следење, без реклами.

Апликацијата помага да следиш и да паметиш — не дава медицински совет и не е медицински уред. Секогаш следи ги упатствата на твојот лекар.

---

## Graphics needed in Play Console
- **App icon:** 512×512 PNG (the brain icon — can be exported from the app icon).
- **Feature graphic:** 1024×500 PNG (banner).
- **Phone screenshots:** 2–8 images. Take them on the phone: Today, the red alarm screen, Well-being, History.

## Required Play Console forms (answers)
- **Privacy policy:** paste the URL above.
- **Data safety:** "No data collected" and "No data shared." (All data stays on device.)
- **Content rating:** complete the questionnaire → will be "Everyone".
- **Target audience:** Adults (18+). Not designed for children.
- **App access:** All functionality available without login.
- **Health apps declaration:** declare it as a medication-reminder tool (not a medical device); the in-app disclaimer covers this.
- **Permissions declaration:**
  - *Exact alarm (USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM):* core feature is an alarm/reminder → declare "Alarm/Reminder".
  - *Full-screen intent (USE_FULL_SCREEN_INTENT):* used to show the alarm full-screen when locked → declare for an alarm app.

## Releasing
1. Create the app in Play Console → upload `ParkinsonCare.aab` to a release.
2. New personal developer accounts: you must run **Closed testing with 20 testers for 14 days** before Production.
3. Fill the store listing (text above) + graphics + the forms above → submit for review.

## To publish an update later
Bump `versionCode`/`versionName` in `android/app/build.gradle`, push to main → CI builds a new signed `ParkinsonCare.aab` → upload it to a new Play release.
