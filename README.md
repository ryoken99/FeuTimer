# FEU BJJ Cronómetro

Android TV / Android timer for FEU BJJ Portugal · Pinhal Novo (Kotlin + Jetpack Compose), with a browser TV simulator and a Wi-Fi phone "Referee Console".

## Layout
- `app/` – Android app (`app/src/main/java/pt/feubjj/timer`: `domain` engine, `ui` screens/components/viewmodel, `audio`, `data`; `assets/referee/index.html` is the phone console served by the TV)
- `web/` – 1:1 browser simulator (`web/js`), dev server `web/serve.cjs`, tests in `web/tests`
- `tools/video` – scripts used to record the showcase video
- `docs/` – design spec, function/module reference, how-to-run guide
- `dist/` – signed APKs, including the current v1.4.0 build

## Run
- Simulator: `node web/serve.cjs` → http://127.0.0.1:8788/ (phone console at `/r`)
- Web tests: `node --test web/tests/timer-web.test.cjs`
- Android: JDK 21 + Android SDK, then `./gradlew testDebugUnitTest assembleRelease`
- Android TV installation and upgrades: [Guia de instalação na TV](tools/tv-install/INSTALAR-NA-TV.md). Download the APK or the TV bundle from [GitHub Releases](https://github.com/ryoken99/FeuTimer/releases/latest).

See `docs/guides/how-to-run-and-install.md` and `docs/reference/functions-and-modules.md` for details.
