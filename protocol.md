# MRP Entwicklungsprotokoll

## 1. Architektur

REST API Server zur Verwaltung von Medieninhalten (Filme, Serien, Spiele) mit Benutzerauthentifizierung, Bewertungen und Empfehlungen.

### Schichtenstruktur
```
org.example/
├── handlers/     → HTTP Request/Response Verarbeitung
├── services/     → Geschäftslogik
├── repositories/ → Datenzugriff
├── models/       → Datenentitäten (User, MediaEntry, Rating)
├── exceptions/   → Custom Exceptions (Validation, NotFound, Conflict)
├── db/           → Datenbankverbindung
└── utils/        → JSON Hilfsfunktionen, UUID Generierung
```

### Technische Entscheidungen
- **Java HttpServer**: Leichtgewichtig, kein Framework-Overhead, volle Kontrolle über HTTP
- **PostgreSQL + JDBC**: Direkte SQL-Kontrolle, parametrisierte Abfragen verhindern SQL-Injection
- **BCrypt (Kostenfaktor 12)**: Industrie-Standard für Passwort-Hashing
- **UUID v7**: Zeitsortierbar, global eindeutig, kein DB-Roundtrip nötig
- **Jackson**: JSON Serialisierung

### Sicherheit
- Bearer Token im Authorization Header für alle authentifizierten Requests
- UUID v7 Tokens in DB gespeichert, ein aktiver Token pro User

### Geschäftslogik
- **Kommentar-Bestätigung**: Sterne-Bewertungen sind immer öffentlich sichtbar. Kommentare werden erst nach Bestätigung via `PUT /api/ratings/{id}/confirm` angezeigt. Bewertungen ohne Kommentar werden automatisch bestätigt
- **Empfehlungen**: Basierend auf 4+ Sterne Bewertungen des Users. Matching nach Genre, Medientyp und Altersfreigabe. Liefert max. 10 Medien mit Durchschnittsbewertung ≥ 3.5

---

## 2. SOLID Prinzipien

### Single Responsibility Principle (SRP)
Jede Schicht hat eine Verantwortung:

| Schicht | Verantwortung |
|---------|---------------|
| Handlers | HTTP Request/Response |
| Services | Geschäftslogik |
| Repositories | Datenzugriff |

**Beispiel**: `RatingService` verarbeitet nur Bewertungslogik, delegiert Datenzugriff an `RatingRepository`:
```java
public class RatingService {
    private final RatingRepository ratingRepository;

    public Rating createRating(UUID mediaId, UUID userId, int stars, String comment) {
        if (ratingRepository.existsByMediaAndUser(mediaId, userId)) {
            throw new ConflictException("Rating already exists");
        }
        return ratingRepository.create(mediaId, userId, stars, comment);
    }
}
```

### Dependency Inversion Principle (DIP)
High-level Module (Services) hängen von Abstraktionen ab, nicht von konkreten Implementierungen:

```java
public class RatingService {
    private final RatingRepository ratingRepository;  // Dependency

    // Dependency wird von außen injiziert → testbar mit Mocks
    public RatingService(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public Rating createRating(UUID mediaId, UUID userId, int stars, String comment) {
        return ratingRepository.create(mediaId, userId, stars, comment);
    }
}

// In Main.java: Konkrete Implementierung wird injiziert
RatingRepository ratingRepo = new RatingRepository();
RatingService ratingService = new RatingService(ratingRepo);
```

**Vorteil**: Services können mit Mock-Repositories getestet werden, ohne echte DB-Verbindung.

---

## 3. Unit Tests

**Framework**: JUnit 5 + Mockito 5.14 | **Anzahl Tests**: 44

| Testklasse | Tests | Abdeckung |
|------------|-------|-----------|
| AuthServiceTest | 6 | Registrierung (Validierung, Duplikate), Login (Passwort-Prüfung) |
| RatingServiceTest | 7 (+5 parametrisiert) | Sterne 1-5, Auto-Bestätigung, Ownership, Duplikate, Likes |
| MediaServiceTest | 5 (+4 parametrisiert) | Titel/Typ-Validierung, Creator-Only Edit/Delete |
| UserServiceTest | 4 | Profil-Ownership, Username-Konflikte, Not-Found |
| FavoriteServiceTest | 3 | Media-Not-Found, Duplikate, Remove-Not-Found |
| JsonHelperTest | 6 | JSON/Query-Parameter Parsing |
| UUIDGeneratorTest | 3 | UUID v7 Generierung und Validierung |

### Teststrategie
- **Constructor Injection**: Services erhalten Repository-Dependencies via Konstruktor für Testbarkeit
- **Mockito Mocks**: Repositories werden gemockt, um Service-Logik isoliert zu testen
- **Echte Service-Tests**: Tests prüfen die tatsächliche Produktions-Implementierung, keine duplizierten Helper-Methoden
- **Parametrisierte Tests**: `@ParameterizedTest` für effizientes Testen mehrerer Eingabevarianten

### Warum diese Tests?

**Priorisierung nach Risiko und Geschäftswert:**

| Priorität | Bereich | Begründung |
|-----------|---------|------------|
| **Kritisch** | AuthService | Sicherheitsrelevant: Falsche Validierung ermöglicht unbefugten Zugriff. Username (3-50 Zeichen) und Passwort (min. 6 Zeichen) müssen vor DB-Speicherung validiert werden. |
| **Kritisch** | RatingService | Kernfunktionalität: Doppelte Bewertungen verhindern, Sterne-Bereich (1-5) erzwingen, Ownership bei Edit/Delete prüfen. Auto-Confirm-Logik für Kommentare. |
| **Hoch** | MediaService | Datenintegrität: Nur gültige Medientypen (movie/series/game) erlauben. Creator-Only-Berechtigungen verhindern unbefugte Änderungen. |
| **Hoch** | UserService | Benutzerdaten: Username-Eindeutigkeit bei Änderungen, Profil-Ownership, Längenvalidierung. |
| **Mittel** | FavoriteService | Duplikat-Vermeidung, Existenzprüfungen für Media und Favorites. |

**Warum Service-Layer testen (nicht Handler)?**
- Services enthalten die **Geschäftslogik** - hier passieren die wichtigen Entscheidungen
- Handler sind nur HTTP-Wrapper - bereits durch Postman-Integration-Tests abgedeckt
- Mit Mockito können Repository-Abhängigkeiten isoliert werden → schnelle, deterministische Tests

**Warum diese Exception-Tests?**
Jede Exception repräsentiert einen **Geschäftsregel-Verstoß**:
- `ValidationException`: Ungültige Eingabedaten (z.B. Sterne < 1 oder > 5)
- `ConflictException`: Duplikate verhindern (bereits bewertet, Username vergeben)
- `UnauthorizedException`: Ownership-Verletzung (fremde Bewertung bearbeiten)
- `NotFoundException`: Referenzielle Integrität (Media existiert nicht)

### Integration Tests
Postman Collection für alle Endpoints: Auth, Media CRUD, Ratings, Favorites, Leaderboard, Recommendations.

---

## 4. Erkenntnisse

- **Pure HTTP vs Framework**: Mehr manueller Routing-Code, aber besseres Verständnis von HTTP-Grundlagen
- **Manuelles JDBC**: Mehr Boilerplate als ORM, aber explizite Kontrolle über Queries und SQL-Injection-Schutz
- **Security-First**: BCrypt + parametrisierte Queries von Anfang an verhindert Sicherheitslücken

---

## 5. Probleme & Lösungen

| Problem | Lösung |
|---------|--------|
| `NullPointerException` beim Lesen von Request-Body | `InputStream` mit `BufferedReader` vollständig lesen, auf `null` prüfen bevor `ObjectMapper.readValue()` |
| SQL-Fehler "column not found" bei INSERT | Spaltennamen in SQL genau mit Datenbank-Schema abgleichen (`snake_case` vs `camelCase`) |
| `Connection refused` bei PostgreSQL | Docker-Container Status prüfen mit `docker ps`, Port 5432 in `docker-compose.yml` verifizieren |
| 405 Method Not Allowed obwohl Handler existiert | Request-Methode im Handler prüfen: `exchange.getRequestMethod().equals("POST")` |
| Token wird nicht erkannt nach Login | Authorization Header mit "Bearer " Prefix senden: `Bearer <token>` |

---

## 6. Zeiterfassung

| Aufgabe | Stunden |
|---------|---------|
| Setup (Projekt, DB, Docker) | 6 |
| Architektur-Design & Planung | 4 |
| User Authentifizierung (inkl. BCrypt, Token) | 12 |
| Media-Entry CRUD | 10 |
| Ratings + Comments + Likes | 14 |
| Sortieren + Filter | 6 |
| Favoriten | 5 |
| Empfehlungen-Algorithmus | 8 |
| Leaderboard | 4 |
| Unit Tests (44) | 12 |
| Postman Tests & Debugging | 6 |
| Dokumentation & Protokoll | 3 |
| **Gesamt** | **90** |

---
