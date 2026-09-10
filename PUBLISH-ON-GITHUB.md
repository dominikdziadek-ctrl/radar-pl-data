# Jednorazowa publikacja na GitHub

1. Wejdź na `https://github.com/new`.
2. W polu **Repository name** wpisz `radar-pl-data`, wybierz **Public** i utwórz puste repozytorium.
3. Rozpakuj paczkę `RadarPL-GitHub-repository-0.4.0.zip`.
4. W repozytorium wybierz **uploading an existing file** i przeciągnij zawartość folderu `radar-pl-data`, łącznie z folderem `.github`. Zatwierdź przez **Commit changes**.
5. Otwórz **Settings → Pages** i w polu **Source** wybierz **GitHub Actions**.
6. Otwórz **Settings → Actions → General**. W sekcji **Workflow permissions** wybierz **Read and write permissions** i zapisz.
7. Otwórz **Actions → Update radar data → Run workflow**, zaznacz `refresh_osm` i uruchom.
8. Po zakończeniu otwórz `https://dominikdziadek-ctrl.github.io/radar-pl-data/manifest.json`.

Jeżeli adres zwróci poprawny JSON, kanał jest gotowy. Od tej chwili harmonogram pobiera CANARD codziennie i OSM w każdą niedzielę. Aplikację 0.4 można zainstalować przed publikacją, ale do uruchomienia GitHub Pages zachowa wbudowaną bazę i przy próbie aktualizacji pokaże błąd HTTP.

Do publicznego repozytorium nie należy dodawać prywatnego folderu `signing/` ani plików `*.keystore`.
