As of April 10, 2026, here’s the practical end-to-end flow to publish your Android app to Google Play.

1. Create a release keystore (one time)
```powershell
keytool -genkeypair -v `
  -keystore C:\keys\text-alarm-upload.jks `
  -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000
```

2. Build release App Bundle (`.aab`)
```powershell
cd E:\workspace\BrightAlarm
.\gradlew.bat --stop
.\gradlew.bat clean
.\gradlew.bat bundleRelease
```
Output:
```powershell
ii .\app\build\outputs\bundle\release
```

If your release signing config is not set in Gradle yet, use:
```powershell
.\gradlew.bat bundleRelease `
  -Pandroid.injected.signing.store.file="C:\keys\text-alarm-upload.jks" `
  -Pandroid.injected.signing.store.password="YOUR_STORE_PASSWORD" `
  -Pandroid.injected.signing.key.alias="upload" `
  -Pandroid.injected.signing.key.password="YOUR_KEY_PASSWORD"
```
