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
- **Kommentar-Bestätigung**: Bewertungen ohne Kommentar werden automatisch bestätigt. Mit Kommentar ist manuelle Bestätigung via `PUT /api/ratings/{id}/confirm` erforderlich
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

### Liskov Substitution Principle (LSP)
Alle Handler implementieren `HttpHandler` und sind im Router austauschbar:

```java
public class Router implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/auth")) authHandler.handle(exchange);
        else if (path.startsWith("/api/media")) mediaHandler.handle(exchange);
        else if (path.startsWith("/api/ratings")) ratingHandler.handle(exchange);
        // Alle Handler austauschbar - LSP
    }
}
```

---

## 3. Unit Tests

**Framework**: JUnit 5 + Mockito | **Anzahl Tests**: 42

| Testklasse | Tests | Abdeckung |
|------------|-------|-----------|
| AuthHandlerTest | 7 | Passwort-Hashing, Token-Extraktion, Validierung |
| MediaHandlerTest | 9 | Medientyp, Titel, Sortierparameter-Validierung |
| RatingHandlerTest | 9 | Sterne-Validierung (1-5), Auto-Bestätigung |
| UserHandlerTest | 8 | Leaderboard-Sortierung, Empfehlungen |
| JsonHelperTest | 6 | JSON/Query-Parameter Parsing |
| UUIDGeneratorTest | 3 | UUID v7 Generierung |

### Teststrategie
- **Parametrisierte Tests**: Effizientes Testen mehrerer Eingabevarianten (z.B. Sterne 0-6)
- **Grenzwert-Tests**: Edge Cases bei Validierung (leere Eingaben, Null-Werte)
- **Isolation**: Mockito mockt Datenbankabhängigkeiten
- **Fokus auf Geschäftslogik**: Validierung auf Handler-Ebene

### Warum diese Tests?
Getestet wird primär die **Eingabevalidierung**, da fehlerhafte Benutzereingaben die häufigste Fehlerquelle sind. Sterne-Bewertungen (1-5), Username-Format und UUID-Parsing werden validiert bevor sie die Datenbank erreichen - das verhindert inkonsistente Daten und unklare SQL-Fehler.

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
| Ungültiges JSON verursachte 500-Fehler | Try-catch für `JsonParseException`, return 400 mit klarer Fehlermeldung |
| UUID als String war fehleranfällig | Native `UUID` Typ + `parseUUID()` Validierung vor DB-Zugriff |
| Statische Methoden erschwerten Testing | Refactoring zu Singleton Pattern für Database-Klasse |

---

## 6. Zeiterfassung

| Aufgabe | Stunden |
|---------|---------|
| Setup (Projekt, DB, Docker) | 18 |
| User Authentifizierung | 6 |
| Media-Entry CRUD | 13 |
| Ratings + Comments + Likes | 2 |
| Sortieren + Filter | 3 |
| Favoriten | 3 |
| Empfehlungen | 4 |
| Leaderboard | 2 |
| Unit Tests (38) | 5 |
| Postman Tests & Debugging | 4 |
| Dokumentation | 3 |
| **Gesamt** | **63** |

---

## 7. Git Repository

[GIT_LINK]
