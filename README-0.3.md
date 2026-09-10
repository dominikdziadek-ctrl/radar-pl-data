# Radar PL 0.3.0 — średnia prędkość OPP

Zaktualizowana natywna aplikacja Android, Android 8+, bez konta i własnego serwera. Wersja 0.3.0 (versionCode 3) ma ten sam identyfikator i ten sam klucz testowy co 0.1.0 i 0.2.0. Można instalować ją jako aktualizację, bez odinstalowywania poprzedniej wersji.

## Średnia prędkość OPP

- Licznik zaczyna się po wykrytym przekroczeniu początku OPP, w obu kierunkach. Nie wymaga znanego limitu prędkości.
- Średnia = suma odległości między kolejnymi pozycjami GPS / czas od wjazdu. Postoje wliczają się do czasu; szum pozycji przy prędkości GPS poniżej 0,5 m/s na obu próbkach nie dodaje drogi. Brak odczytu prędkości nie jest traktowany jako potwierdzenie postoju.
- Czas wjazdu i wyjazdu oraz części drogi między próbkami są interpolowane. Używany jest monotoniczny czas pozyskania pozycji, więc zmiana zegara telefonu nie zmienia średniej.
- Pierwsza liczba po 5 sekundach. Wyświetlane są też długość śladu i czas od wjazdu.
- Po wyjeździe wynik zostaje jako OSTATNI OPP do następnego OPP lub zatrzymania usługi. Aktualizacja kamer nie kasuje podsumowania. Wynik nie jest zapisywany po zamknięciu procesu/usługi.
- Przerwa GPS ponad 5 sekund, dokładność gorsza niż 40 m lub skok ponad 85 m/s unieważniają średnią całego bieżącego przejazdu; aplikacja pokazuje niepełny ślad zamiast wiarygodnie wyglądającej liczby. Start aplikacji wewnątrz OPP nie odtwarza wjazdu.
- Wynik jest szacunkiem GPS, nie pomiarem urzędowym. Kręta trasa jest sumą kolejnych odcinków śladu, bez dopasowania do geometrii drogi. Rozpoznanie bram nadal jest przybliżone (tolerancja 45 m).
- Niezależna od Androida klasa `OppAverage` oddziela obliczenia od interfejsu i ułatwia późniejsze przeniesienie na ESP.

## Zachowane limity z wersji 0.2

- Widoczne pole **LIMIT** z datą i wyjaśnieniem źródła, działające offline.
- Limity z OSM dla **386 z 499 rekordów fotoradarów** i **114 ze 137 rekordów OPP** (co najmniej jeden kierunek; 209 kierunków łącznie).
- Pozostałe miejsca wyświetlają kreskę / informację o braku jednoznacznych danych. Nie ma zgadywania domyślnych ani warunkowych ograniczeń.
- Widoczna liczba pomiarów z przypisanym limitem: początkowo 500/636.
- Zachowanie limitów przy aktualizacji CANARD, gdy ID i współrzędne nadal pasują. Przesunięcie początku/końca o ponad 10 m unieważnia przypisanie.
- Osobna data OSM: **2026-09-09T20:04:03Z** (najstarszy znacznik czasu wyciągów użytych do opracowania).
- Import nowszej paczki `osm-limits.json` przez systemowy wybór pliku; bez dostępu do całej pamięci telefonu.
- Dane OSM starsze niż 90 dni są ukrywane. To techniczny próg odświeżania, nie gwarancja aktualności znaków przez 90 dni.
- Symulacja pokazuje fikcyjne limity 50 i 70 km/h, oznaczone DEMO.

## Instalacja i test

1. Otwórz APK 0.3.0 na telefonie i zaakceptuj aktualizację. Przy pierwszej instalacji Android może poprosić o zezwolenie na instalację z wybranej aplikacji.
2. Na postoju wybierz Symulacja (około 2 minut). Powinny wystąpić: alarm radaru, limit 50, alarm i wjazd OPP z limitem 70 oraz koniec OPP. Podczas OPP średnia wynosi około 72,1 km/h (wynika ze śladu), a po wyjeździe zostaje podsumowanie. To dane demonstracyjne.
3. Uruchom jazdę z GPS, zezwól na dokładną lokalizację i powiadomienia. Sprawdź głośność powiadomień.
4. Na znanej trasie pasażer porównuje limit przy urządzeniu z oznakowaniem. Zanotuj lokalizację, kierunek i ewentualną różnicę.
5. Sprawdź działanie po wygaszeniu ekranu. Nie było jeszcze testu na fizycznym telefonie ani emulatorze.

**Limit dotyczy miejsca pomiaru, niekoniecznie bieżącej pozycji auta.** Nie dodano alarmu przekroczenia limitu na dojeździe: niższy limit może zaczynać się dopiero bliżej kamery. Dla OPP wyświetlamy limit tylko w obsługiwanym kierunku. Średnia OPP jest niezależnym szacunkiem GPS.

## Jak przypisano limity

Profil: samochód osobowy bez przyczepy. Wykorzystujemy wyłącznie jawne liczbowe wartości 10–140 km/h (oraz zapis km/h), z obsługą kierunków przy analizie trasy. Odrzucamy ograniczenia warunkowe/zmienne, nieobsługiwane wartości i sprzeczności.

Fotoradary: bezpośredni znacznik `highway=speed_camera` OSM w odległości najwyżej 20 m od CANARD może dostarczyć limit przypisany do urządzenia. Sprawdzamy zgodność pobliskich znaczników; konflikt ze zgodnie ustalonym limitem drogowym blokuje wartość. Alternatywnie korzystamy z pobliskich dróg samochodowych, gdy są dostatecznie blisko i mają zgodne limity. Rozróżnienie dowodu (node/way) i odległości zachowano w paczce. Znacznik kamery często jest obok jezdni, więc brak członkostwa w drodze nie przekreśla bezpośredniego limitu urządzenia.

OPP: relacja `enforcement=average_speed` musi mieć jednoznaczne punkty from/to odpowiadające końcom CANARD (do 90 m), kompletną połączoną geometrię bez rozgałęzień, spójny kierunek przejazdu i ten sam jawny limit na wszystkich odcinkach. Limit relacji, jeśli podany, również musi być zgodny. Sprawdzamy oba kierunki osobno. Zmienny limit na trasie oznacza brak jednej wartości dla całego OPP.

Są to dopasowania mapowe, **nie terenowa weryfikacja bieżących znaków**. Mogą wystąpić zmiany tymczasowe i błędy mapy. Rozpoznawanie faktycznej drogi auta pozostaje przybliżone, tak jak w 0.1.0. Widok to schemat punktów, bez pełnej mapy dróg. Liczby dotyczą rekordów, nie audytu liczby czynnych instalacji.

## Aktualizacje bez serwera

Automatyczna aktualizacja CANARD przez Wi-Fi nadal działa podczas korzystania z aplikacji/usługi, na postoju, poza OPP, najwyżej raz na dobę. **Odświeża lokalizacje kamer; nie aktualizuje daty ani wartości OSM.** Limity są osobną paczką offline. Nowszą paczkę można wczytać przyciskiem w aplikacji po zatrzymaniu jazdy. Nowe lub przesunięte kamery bez zgodnego przypisania pozostają bez limitu.

Ta wersja nie pobiera automatycznie limitów z publicznego Overpass. Nie wykorzystuje publicznego serwera jako stałego zaplecza aplikacji i nie pobiera wielogigabajtowej mapy na telefon. Aktualizacja limitów wymaga nowej paczki lub kolejnej wersji aplikacji.

Paczka przykładowa i użyta w APK: `app/src/main/assets/osm-limits.json`.

## Dane, licencja i odtwarzalność

Kamery: CANARD / GITD, https://www.canard.gitd.gov.pl/cms/o-nas/mapa-urzadzen. Snapshot 9.09.2026: 636 rekordów. Warunki źródła i wcześniejsza funkcjonalność opisane w README-0.1.md.

Limity i geometria: © OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright. Dane pobrano przez https://overpass-api.de/api/interpreter. Paczka wynikowa zawiera przypisania z OSM i jest udostępniona jako pochodna baza na ODbL 1.0. Zachowaj autorstwo i warunki udostępniania bazy przy dalszej dystrybucji. Kod aplikacji jest oddzielony od bazy.

`data/` zawiera otrzymane wyciągi źródłowe, złączone dowody i raport dopasowania. Nie zawiera śladów jazdy użytkownika. Odświeżenie na komputerze (sporadyczne opracowanie danych, z poszanowaniem limitów Overpass):

```sh
python3 tools/fetch_osm.py --refresh
python3 tools/assemble_evidence.py
python3 tools/match_limits.py
```

Wynik: `app/src/main/assets/osm-limits.json`. Obejrzyj raport, uruchom testy i sprawdź zmiany przed dystrybucją. Alternatywny `extract_osm.py` potrafi przetwarzać lokalny PBF Geofabrik, ale jest dużo cięższy; nie był źródłem finalnej paczki 0.2.0.

## Kompilacja i testy

JDK 17, SDK platform 35, build-tools 35.0.0:

```sh
ANDROID_SDK_ROOT=/sciezka/do/sdk python3 build-apk.py
python3 -m unittest discover -s tests -p 'test_*.py'
mkdir -p build/test-classes
java com.sun.tools.javac.Main -d build/test-classes app/src/main/java/pl/radar/offline/OppAverage.java app/src/main/java/pl/radar/offline/Engine.java app/src/main/java/pl/radar/offline/LzString.java tests/EngineTest.java tests/AverageTest.java
java -cp build/test-classes pl.radar.offline.EngineTest
java -cp build/test-classes pl.radar.offline.AverageTest
```

APK: `build/manual/RadarPL-0.3.0.apk`. Alternatywnie projekt Gradle / Android Studio, AGP 8.7.3, Gradle 8.9. Wersję dostarczoną zbudowano bezpośrednio oficjalnymi narzędziami SDK.

W 0.3.0 przeszły 23 dotychczasowe asercje silnika Java i 39 nowych asercji średniej OPP: oba kierunki, części próbek przy bramach, postoje, kręta trasa, brak odczytu prędkości, błędy GPS, zachowanie wyniku i reset. Dopasowanie danych pozostało bez zmian; jego 12 testów Python przeszło w 0.2.0. Obejmują m.in. sprzeczne/warunkowe limity, brakujące fragmenty i rozłączną geometrię OPP, kierunek jednokierunkowy, różne limity kierunkowe, utratę GPS, ukrywanie nieznanych limitów oraz scenariusz przejazdu. Testy syntetyczne nie zastępują sprawdzenia oznakowania na drodze. Kompilacja DEX/APK i podpis sprawdzane przy budowaniu. Bez testu urządzeniowego.

Klucz `signing/debug.keystore`, hasło `android`, alias `androiddebugkey` służy wyłącznie do prototypu. Zachowano klucz 0.1.0, aby aktualizacja nie wymagała odinstalowania aplikacji.
