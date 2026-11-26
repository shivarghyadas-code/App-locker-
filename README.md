# App Locker

Android app that puts a password screen in front of bank and UPI apps (PNB ONE, YONO SBI,
PhonePe, Google Pay, Paytm, BHIM and any others you choose). Built for a Poco M3 (MIUI);
works on any Android 8+ phone.

Made for one situation: **the phone is snatched while it is unlocked** and someone tries to
send the money out.

- **Password only.** No fingerprint or pattern, and it is separate from the phone's screen lock.
  At least 8 characters with letters and numbers. Only a salted PBKDF2 hash is stored.
- **Locks again quickly.** An app locks when you leave it, when the screen turns off, and when
  it is left open and untouched for 1 minute (you can choose 30 s – 5 min).
- **Can't be switched off without the password.** Settings, App info and uninstall screens are
  locked too, so App Locker can't be turned off, force-stopped or removed.
- **Slows down guessing.** After 5 wrong tries the wait is 30 s, then 1 min, 2 min, and so on
  up to 15 min.
- **Fails closed.** If the phone blocks the password screen, the bank app is sent to the home
  screen instead of being left open.
- **Night lock with trusted places.** From 10 PM to 6 AM (you can change the hours), money
  apps open only when the phone is inside a trusted place you marked on the map (home, auto
  stand…). Anywhere else they stay blocked, even with the password. Settings and App Locker
  are blocked too, so it can't be switched off. Fake GPS apps are detected.
- Doesn't read the screen or what you type. Screenshots of the password screen are blocked.
  Internet is used only to show the map; location is checked only while the lock screen is open
  and never leaves the phone.

**Download:** see [Releases](../../releases/latest) → `App-Locker.apk`

## Setup (Poco M3)
1. Install `App-Locker.apk` (allow "Install unknown apps").
2. Open App Locker and create the password.
3. Tap **Turn on App Locker**, find App Locker, turn it on, then wait for the timer and tap Allow.
4. Do the 3 MIUI steps on the same screen: pop-up windows, Autostart, Battery "No restrictions".
5. Check the list of apps to lock. Money apps are already ticked.
6. Night lock: allow location ("While using the app"), tap **Add trusted place on map**, put the
   pin on your home, choose the size (for example 150 m) and save. Add other safe places the same way.

## Also do this on the phone
- Set a **phone screen lock** (PIN). Restarting in Safe mode turns off every downloaded app,
  App Locker included, so the screen lock is what stops that.
- Turn on **Find device** (Mi account / Google) so a stolen phone can be locked or erased remotely.
- Never use the same number for the App Locker password, the screen lock and the UPI PIN.

**Forgot the password?** Restart the phone in Safe mode (hold Power, then long-press Power off
or Reboot), uninstall App Locker, restart, and install it again.
