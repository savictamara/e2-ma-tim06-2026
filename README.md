# Slagalica

Mobilna Android aplikacija inspirisana kvizom Slagalica.

KT1 implementacija obuhvata GUI deo aplikacije za:
- registraciju i logovanje korisnika
- prikaz profila
- igre:
  - Korak po korak
  - Moj broj
  - Ko zna zna
  - Spojnice
  - Asocijacije
  - Skočko
- notifikacije

Aplikacija je implementirana korišćenjem:
- Java
- Android Studio
- XML layout-a
- Gradle (Groovy DSL)

## Pokretanje aplikacije

1. Otvoriti projekat u Android Studio:
   File -> Open

2. Izabrati root folder projekta i sačekati da se završi Gradle Sync.

3. Na Android telefonu uključiti:
   - Developer options
   - USB debugging

4. Povezati telefon USB kablom i potvrditi:
   Allow USB debugging

5. U Android Studio:
   - izabrati povezani uređaj ili emulator
   - izabrati konfiguraciju `app`
   - kliknuti `Run`


## Napomena

KT1 predstavlja GUI implementaciju aplikacije.

---

# KT2

KT2 implementacija obuhvata punu funkcionalnost (prezentacioni deo, poslovnu logiku i
upravljanje podacima preko Firebase-a) za:
- registraciju i logovanje korisnika (potvrda mejlom, reset lozinke)
- prikaz profila (avatar, tokeni, zvezde, liga, region, QR kod, statistika)
- igre koje se igraju jedan na jedan preko Firestore-a:
  - Korak po korak
  - Moj broj (uključujući stopiranje brojeva preko shake senzora)
  - Ko zna zna
  - Spojnice
  - Asocijacije
  - Skočko
- sistemske notifikacije (kanali za čet, rangiranje, nagrade i ostalo, istorija,
  oznaka pročitano/nepročitano)

Dodatno u odnosu na KT1, aplikacija koristi:
- Firebase Authentication (registracija, logovanje, reset lozinke)
- Firebase Firestore (partije u realnom vremenu, profil, statistika, notifikacije)

## Pokretanje aplikacije (KT2)

1. Otvoriti projekat u Android Studio:
   File -> Open

2. Izabrati root folder projekta i sačekati da se završi Gradle Sync.

3. Projekat već sadrži `app/google-services.json` povezan sa Firebase projektom
   (Authentication + Firestore), tako da nije potrebna dodatna konfiguracija.

4. Na Android telefonu ili emulatoru (sa Google Play Services) uključiti:
   - Developer options
   - USB debugging

5. Povezati telefon USB kablom i potvrditi:
   Allow USB debugging

6. U Android Studio:
   - izabrati povezani uređaj ili emulator
   - izabrati konfiguraciju `app`
   - kliknuti `Run`

7. Uređaj/emulator mora imati pristup internetu (Firebase Authentication i
   Firestore se koriste za logovanje, partije, profil i notifikacije).

8. Na prvom pokretanju dozvoliti slanje notifikacija (Android 13+ traži
   `POST_NOTIFICATIONS` dozvolu).

## Testiranje partija jedan na jedan

Igre se igraju u realnom vremenu preko Firestore-a, pa je za testiranje partije
potrebno pokrenuti aplikaciju na dva uređaja/emulatora (ili dva korisnička naloga)
istovremeno, kako bi se dva igrača spojila u istu partiju. Oba uređaja treba da
budu povezana na istu Wi-Fi mrežu.

## Napomena

KT2 predstavlja implementaciju cele funkcionalnosti za registraciju i logovanje,
profil korisnika, notifikacije i igre Korak po korak, Moj broj, Ko zna zna,
Spojnice, Asocijacije i Skočko.
