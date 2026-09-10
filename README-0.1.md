# Radar PL — prototyp Android 0.1.0

Natywna aplikacja Android (Java), min. Android 8.0, target Android 15. Bez konta, reklam, zewnętrznych bibliotek aplikacji i własnego serwera. APK podpisany kluczem testowym.

## Pierwsze uruchomienie

1. Zainstaluj `RadarPL-0.1.0.apk`. Android może poprosić o jednorazowe zezwolenie na instalację z wybranej aplikacji/pliku.
2. Na postoju wybierz **Symulacja**. Działa offline, bez GPS i bez udzielania uprawnień. Trwa około 126 sekund. Oczekiwane zdarzenia: fotoradar, zbliżanie do OPP, wjazd, koniec.
3. Włącz lokalizację, wybierz **Rozpocznij jazdę z GPS**. Przyznaj dokładną lokalizację „Podczas używania aplikacji” oraz powiadomienia. Z początkowym fixem poczekaj przy szybie / na zewnątrz.
4. Sprawdź dźwięk powiadomień; tryb cichy/Nie przeszkadzać może wyciszać alarmy.
5. Uruchomiona usługa lokalizacji działa także po wygaszeniu ekranu. Zatrzymanie: przycisk w aplikacji lub powiadomieniu. Producent telefonu może dodatkowo ograniczać działanie w tle; trzeba to przetestować. Blokada uśpienia ma limit 4 godzin. Po tym czasie rozpocznij sesję od nowa.
6. Test na znanej trasie: obserwuje pasażer; zanotuj miejsce, kierunek, komunikat, dystans i ewentualny fałszywy alarm. Warto sprawdzić także przejazd w przeciwną stronę i sąsiednią drogę.

## Dane i aktualizacja

Wbudowano snapshot mapy CANARD pobrany 9.09.2026: 499 rekordów PP i 137 rekordów OPP. To liczby rekordów poglądowej mapy, a nie audyt czynnych instalacji. Każdy OPP ma dwie pary współrzędnych.

Źródło: https://www.canard.gitd.gov.pl/cms/o-nas/mapa-urzadzen
CANARD / Główny Inspektorat Transportu Drogowego. Strona deklaruje bezpłatne korzystanie z treści bez zgody GITD; treści oznaczone prawami autorskimi na CC BY 4.0, o ile nie wskazano inaczej. Zachowano wskazanie źródła w aplikacji i projekcie. Dane mapy są poglądowe.

Przycisk aktualizacji pobiera stronę przez HTTPS, wydobywa jej osadzone bloki LZ-string Base64 i waliduje JSON, zakres współrzędnych, duplikaty ID, długości odcinków i liczbę rekordów. AtomicFile zachowuje poprzednią bazę przy przerwanym zapisie. Odrzucamy spadek liczby rekordów o ponad 25%. Jeśli zmieni się format strony, aktualizacja może wymagać poprawki kodu; dotychczasowa baza działa nadal.

Automatyczna próba: najwyżej raz na dobę na zweryfikowanym Wi-Fi, gdy aplikacja jest otwarta lub działa usługa jazdy, na postoju i poza aktywnym OPP. Próba ręczna może używać również danych komórkowych. Nie ma harmonogramu aktualizacji po całkowitym zamknięciu/zatrzymaniu aplikacji. Data w interfejsie oznacza czas pobrania, nie aktualność każdej kamery. Nie wysyłamy współrzędnych ani śladu przejazdu.

## Zakres i ograniczenia

- Widok punktów w promieniu 1 km, północ u góry, bez podkładu dróg. To schemat sytuacyjny, nie pełna mapa/nawigacja.
- Prędkość z GNSS, dźwięk i pasek ostrzeżenia; dystans w linii prostej.
- Próg ostrzegania 25 sekund jazdy, ograniczony do 250–1000 m; wymagany ruch w stronę punktu i prędkość ≥2 m/s. Wyciszenie powtórki 3 minuty.
- CANARD nie podaje w tym eksporcie limitów ani kierunków; aplikacja ich nie zgaduje. Pokazuje „limity nieznane”.
- OPP: heurystyczne przecięcie sąsiedztwa jednego końca, z ruchem w stronę drugiego. Nie potwierdza przynależności do właściwej drogi/jezdni. Drugi koniec zamyka OPP. Zjazd z odcinka wymaga ręcznego resetu; awaryjny timeout 2 godziny. Uruchomienie wewnątrz OPP nie odtwarza wjazdu.
- Brak średniej OPP: brak zweryfikowanej długości i geometrii. Utrata GPS oznacza niepewny przebieg. Wznowienie po przerwie nie interpoluje przekroczenia bramki.
- Filtry: dokładność >40 m, pozycja starsza niż 5 s, skok >85 m/s i odstęp >5 s. Przy braku świeżego GPS przez 10 s ekran nie pokazuje aktualnej prędkości.
- Możliwe fałszywe alarmy z sąsiednich dróg oraz braki danych. Nie potwierdzamy czynności ani kompletności wszystkich punktów CANARD. Brak kontroli mobilnych.
- Tryb symulacji działa tylko w aktywnej instancji ekranu; obrót/restart kończy symulację. Sesja GPS jest w osobnej usłudze.

## Architektura do dalszego portowania

`Engine.java` — czysta logika bez Android API, jednostki SI, czas monotoniczny; modele Camera/Fix/Result. Algorytm można przepisać do C++ i uruchomić te same scenariusze. Nie jest to jeszcze kod kompilowalny na ESP32.
`Database.java` — adapter CANARD + atomowy zapis.
`LzString.java` — dekoder formatu kompresji CANARD.
`RadarService.java` — lokalizacja Android i powiadomienie usługi.
`MainActivity.java` — natywny interfejs i symulacja.

## Kompilacja

Android Studio: otwórz katalog, SDK 35, JDK 17, Gradle 8.9 / AGP 8.7.3, zadanie `assembleDebug`.

Alternatywa bez Gradle (użyta do dostarczonego APK): JDK 17 + Android SDK platform 35 i build-tools 35.0.0. Ustaw `ANDROID_SDK_ROOT` i opcjonalnie `JAVA_HOME`, uruchom:

```sh
python3 build-apk.py
```

Wynik: `build/manual/RadarPL-0.1.0.apk`.
Klucz `signing/debug.keystore` służy tylko do tego prototypu. Hasło `android`, alias `androiddebugkey`. Zachowaj go dla instalowania następnych wersji bez utraty danych. Nie używać jako klucza produkcyjnego.

## Testy

```sh
mkdir -p /tmp/radar-tests
java com.sun.tools.javac.Main -d /tmp/radar-tests app/src/main/java/pl/radar/offline/Engine.java app/src/main/java/pl/radar/offline/LzString.java tests/EngineTest.java
java -cp /tmp/radar-tests pl.radar.offline.EngineTest
```

Opcjonalny argument: ścieżka do pobranego HTML mapy CANARD. Dodaje sprawdzenia dekodowania danych rzeczywistych.

Wykonano 20 asercji z rzeczywistym HTML: odległość, zbliżanie/oddalanie, cooldown, słaby GPS/skok, oba kierunki OPP, przerwa GPS, wyjazd, sąsiednia droga 200 m, start wewnątrz odcinka, pełna symulacja UI oraz dekodowanie PP/OPP. Wszystkie 636 rekordów przeszło kontrolę zakresu współrzędnych i długości cięciwy odcinka. Skompilowano klasy Android, DEX, manifest, APK i zweryfikowano podpis v2/v3.

Nie wykonano testu na fizycznym Androidzie ani emulatorze. Gradle/lint nie uruchomiły się z powodu niedostępności artefaktu w repozytorium w środowisku budowania; APK zbudowano oficjalnymi narzędziami SDK bez Gradle. Praca GPS w tle, faktyczny odbiór powiadomień/dźwięków i wygląd na telefonie wymagają testu instalacyjnego.
