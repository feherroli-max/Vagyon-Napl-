# Vagyon Napló – natív Android alkalmazás

Kotlin + Jetpack Compose, Android 8.0+, magyar nyelven. A teljes forráskódot tartalmazó projekt, GitHub Actions workflow-val.

## Funkciók
- Több, egyedileg átnevezhető személy és gyors személyváltás.
- Személyenként tetszőleges pénzkategória, ikon- és színválasztás, nulla egyenlegű kategóriák archiválása.
- Forintban megadott egyenlegek, az előző mentéshez viszonyított élő Ft- és %-os eltérés.
- Dátumos/időpontos pillanatkép, opcionális megjegyzéssel.
- Mentési előzmények szerkesztése és törlése.
- Statisztika személyenként és családi összesítésben, kategória- és dátumszűrővel, vonaldiagrammal, nyitó/záró állománnyal és megjegyzésekkel.
- Egyedi, a telefon feloldókódjától független jelszó; PBKDF2-HMAC-SHA256 (240 000 iteráció), AES-256-GCM titkosított adattárolás.
- Automatikus zárolás háttérbe helyezéskor, képernyőképek tiltása, jelszóváltás.
- Titkosított biztonsági mentés és visszaállítás Android dokumentumválasztóval.

## APK készítése
1. Töltsd fel a projekt **tartalmát** GitHub repository gyökerébe (a rejtett `.github` mappával együtt).
2. GitHub → Actions → Android APK → Run workflow, vagy push a `main`/`master` ágra.
3. A futás után töltsd le a `Vagyonnaplo-APK` artifactot, majd az `app-debug.apk` fájlt.

## Fontos tudnivalók
- Ez forráskód, **nem előre lefordított és tesztelt APK**. Ebben a környezetben nem állt rendelkezésre Android SDK/Gradle build a tényleges fordítás ellenőrzéséhez.
- Az alkalmazás nem csatlakozik bankokhoz, és nem számol befektetési hozamot; a vagyonállomány változását követi.
- A titkosított fájl helyreállításához szükség van az eredeti jelszóra; nincs online jelszó-visszaállítás.
- Az időintervallum nyitó értéke a kezdetkor utoljára ismert állapotból számítódik; ha korábban nem volt adat, nulla.
- A statisztikában a családi összesítés minden személy utolsó ismert értékét veszi figyelembe, így az eltérő mentési időpontok nem okoznak hiányzó személyeket.

## Visszadátumozás
Új mentéskor a dátum a mentés gomb felett módosítható a naptárikonra kattintva; az előzmények javításánál a rögzítés dátuma is átírható. A jövőbeli dátumok nem választhatók.
