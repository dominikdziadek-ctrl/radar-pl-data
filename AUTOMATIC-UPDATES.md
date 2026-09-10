# Automatyczne aktualizacje Radar PL

Docelowe repozytorium: `dominikdziadek-ctrl/radar-pl-data`.

## Harmonogram

- Codziennie o 02:17 UTC: pobranie i walidacja lokalizacji CANARD.
- W niedzielę: dodatkowe pobranie dowodów OSM i ponowne dopasowanie limitów.
- Ręczne uruchomienie workflow może wymusić aktualizację OSM.

Workflow publikuje `public/manifest.json` oraz dwie paczki gzip. Aplikacja pobiera manifest raz dziennie po Wi-Fi. Większe pliki pobiera tylko po zmianie wersji.

## Zabezpieczenia

- limity wielkości odpowiedzi i czasu połączeń;
- kontrola schematu, współrzędnych, identyfikatorów i liczby rekordów;
- odrzucenie spadku liczby urządzeń o ponad 25% lub wzrostu o ponad 50%;
- limit prędkości tylko dla jawnej, liczbowej i niesprzecznej wartości OSM;
- w OPP jednakowa wartość na pełnej, połączonej trasie i osobna analiza kierunku;
- SHA-256 i deklarowany rozmiar każdego pliku;
- zapis atomowy w aplikacji i zachowanie poprzedniej paczki po błędzie;
- brak aktualizacji podczas aktywnego OPP lub jazdy.

## Pierwsze uruchomienie na GitHub

1. Utworzyć publiczne repozytorium i wgrać projekt bez katalogu `signing/`.
2. W Settings → Pages wybrać źródło **GitHub Actions**.
3. Adres klienta jest ustawiony na `https://dominikdziadek-ctrl.github.io/radar-pl-data/`.
4. Uruchomić workflow **Update radar data** ręcznie z `refresh_osm=true`.
5. Sprawdzić GitHub Pages, manifest, sumy kontrolne i log testów.
6. Zbudować oraz przetestować APK 0.4 na telefonie.

Klucz podpisujący APK pozostaje prywatny. Publiczne repozytorium zawiera kod, skrypty i dane wynikowe, ale nie zawiera klucza.
