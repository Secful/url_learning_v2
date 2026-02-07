# Built-In Validators Guide

This document describes all built-in validators available in the PathTemplateTrie, including how the LLM automatically detects and suggests them.

## Overview

The trie supports **pattern-based** validators (regex) and **set-based** validators (O(1) HashSet lookups) for validating URL segments.

### Performance Comparison

| Validator Type | Performance | Use Case |
|---------------|-------------|----------|
| Pattern-based (UUID, NUMERIC) | ~50 nanoseconds | Small patterns, variable formats |
| Set-based (Airport, Language, Country) | ~15 nanoseconds | Large closed sets (100+ values) |

---

## Pattern-Based Validators

### `SegmentValidator.ANY`
- **Matches**: Any non-empty string
- **Performance**: ~10 nanoseconds
- **Example**: `{name}`, `{slug}`, `{identifier}`
- **Usage**:
  ```java
  validators.put("name", SegmentValidator.ANY);
  ```

### `SegmentValidator.NUMERIC`
- **Matches**: Digits only (e.g., `123`, `456789`)
- **Performance**: ~50 nanoseconds
- **Example**: `{id}`, `{count}`, `{index}`
- **Usage**:
  ```java
  validators.put("id", SegmentValidator.NUMERIC);
  ```

### `SegmentValidator.UUID`
- **Matches**: Standard UUID format
- **Format**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
- **Performance**: ~50 nanoseconds
- **Example**: `550e8400-e29b-41d4-a716-446655440000`
- **Usage**:
  ```java
  validators.put("id", SegmentValidator.UUID);
  ```

---

## Set-Based Validators (Closed Sets)

### Airport Codes

#### `SegmentValidator.IATA_AIRPORT`
- **Matches**: 3-letter IATA airport codes
- **Examples**: `JFK`, `LAX`, `LHR`, `CDG`, `DXB`, `HND`
- **Count**: 150+ major international airports
- **Performance**: ~15 nanoseconds
- **Usage**:
  ```java
  // Manual usage
  validators.put("airport", SegmentValidator.IATA_AIRPORT);
  trie.insert("/flights/{airport}/departures", validators);

  // LLM will auto-detect from paths like:
  // /flights/JFK/status → /flights/{airport}/status
  ```

#### `SegmentValidator.ICAO_AIRPORT`
- **Matches**: 4-letter ICAO airport codes
- **Examples**: `KJFK`, `EGLL`, `LFPG`, `RJTT`
- **Count**: 100+ major international airports
- **Performance**: ~15 nanoseconds
- **Usage**:
  ```java
  validators.put("airport", SegmentValidator.ICAO_AIRPORT);
  ```

---

### Language Codes

#### `SegmentValidator.ISO_639_1_LANGUAGE`
- **Matches**: 2-letter ISO 639-1 language codes
- **Examples**: `en`, `es`, `fr`, `de`, `zh`, `ja`, `ar`
- **Count**: 90+ languages
- **Performance**: ~15 nanoseconds
- **Usage**:
  ```java
  // Manual usage
  validators.put("lang", SegmentValidator.ISO_639_1_LANGUAGE);
  trie.insert("/content/{lang}/articles", validators);

  // LLM will auto-detect from paths like:
  // /content/en/news → /content/{lang}/news
  ```

#### `SegmentValidator.ISO_639_2_LANGUAGE`
- **Matches**: 3-letter ISO 639-2 language codes
- **Examples**: `eng`, `spa`, `fra`, `deu`, `zho`, `jpn`
- **Count**: 90+ languages
- **Performance**: ~15 nanoseconds

---

### Country Codes

#### `SegmentValidator.ISO_3166_COUNTRY_ALPHA2`
- **Matches**: 2-letter ISO 3166-1 alpha-2 country codes
- **Examples**: `US`, `GB`, `FR`, `DE`, `CN`, `JP`, `BR`
- **Count**: 200+ countries
- **Performance**: ~15 nanoseconds
- **Usage**:
  ```java
  // Manual usage
  validators.put("country", SegmentValidator.ISO_3166_COUNTRY_ALPHA2);
  trie.insert("/api/countries/{country}/users", validators);

  // LLM will auto-detect from paths like:
  // /api/countries/US/data → /api/countries/{country}/data
  ```

#### `SegmentValidator.ISO_3166_COUNTRY_ALPHA3`
- **Matches**: 3-letter ISO 3166-1 alpha-3 country codes
- **Examples**: `USA`, `GBR`, `FRA`, `DEU`, `CHN`, `JPN`
- **Count**: 200+ countries
- **Performance**: ~15 nanoseconds

---

### Currency Codes

#### `SegmentValidator.ISO_4217_CURRENCY`
- **Matches**: 3-letter ISO 4217 currency codes
- **Examples**: `USD`, `EUR`, `GBP`, `JPY`, `CNY`, `CHF`
- **Includes**: Cryptocurrencies (`BTC`, `ETH`, `USDT`, etc.)
- **Count**: 150+ currencies
- **Performance**: ~15 nanoseconds
- **Usage**:
  ```java
  // Manual usage
  validators.put("currency", SegmentValidator.ISO_4217_CURRENCY);
  trie.insert("/prices/{currency}/products", validators);

  // LLM will auto-detect from paths like:
  // /prices/USD/items → /prices/{currency}/items
  ```

---

### HTTP Status Codes

#### `SegmentValidator.HTTP_STATUS_CODE`
- **Matches**: 3-digit HTTP status codes
- **Examples**: `200`, `404`, `500`, `301`, `403`, `503`
- **Ranges**: 1xx, 2xx, 3xx, 4xx, 5xx
- **Count**: 60+ standard codes
- **Performance**: ~15 nanoseconds
- **Usage**:
  ```java
  validators.put("status", SegmentValidator.HTTP_STATUS_CODE);
  trie.insert("/errors/{status}/info", validators);
  ```

---

## Custom Set-Based Validators

You can create your own set-based validators for domain-specific closed sets:

```java
// Example: Valid order statuses
SegmentValidator ORDER_STATUS = SegmentValidator.setOf(
    "pending", "processing", "shipped", "delivered", "cancelled", "refunded"
);

// Example: Product categories
SegmentValidator PRODUCT_CATEGORY = SegmentValidator.setOf(
    "electronics", "clothing", "books", "home", "sports", "toys"
);

// Example: User roles
SegmentValidator USER_ROLE = SegmentValidator.setOf(
    "admin", "moderator", "user", "guest", "premium"
);

// Use in path templates
Map<String, SegmentValidator> validators = Map.of(
    "status", ORDER_STATUS,
    "category", PRODUCT_CATEGORY,
    "role", USER_ROLE
);

trie.insert("/orders/{status}/list", validators);
trie.insert("/products/{category}/featured", validators);
trie.insert("/users/{role}/permissions", validators);
```

---

## LLM Integration

The LLM automatically detects and suggests appropriate validators when inferring templates from concrete paths.

### How It Works

1. **Path Analysis**: LLM examines each segment to identify patterns
2. **Validator Selection**: Chooses the most specific validator that matches
3. **Template Generation**: Returns template with validators

### Examples

```java
// Input path → LLM-inferred template with validators

"/flights/JFK/departures"
→ {
    "template": "/flights/{airport}/departures",
    "validators": {"airport": "IATA_AIRPORT"}
  }

"/content/en/articles"
→ {
    "template": "/content/{lang}/articles",
    "validators": {"lang": "ISO_639_1"}
  }

"/api/countries/US/users"
→ {
    "template": "/api/countries/{country}/users",
    "validators": {"country": "COUNTRY_ALPHA2"}
  }

"/prices/USD/products"
→ {
    "template": "/prices/{currency}/products",
    "validators": {"currency": "CURRENCY"}
  }

"/errors/404/details"
→ {
    "template": "/errors/{status}/details",
    "validators": {"status": "HTTP_STATUS"}
  }
```

### Complex Paths

The LLM can handle multiple validators in the same path:

```java
"/flights/JFK/LAX/prices/USD"
→ {
    "template": "/flights/{origin}/{destination}/prices/{currency}",
    "validators": {
      "origin": "IATA_AIRPORT",
      "destination": "IATA_AIRPORT",
      "currency": "CURRENCY"
    }
  }

"/content/en/US/news"
→ {
    "template": "/content/{lang}/{country}/news",
    "validators": {
      "lang": "ISO_639_1",
      "country": "COUNTRY_ALPHA2"
    }
  }
```

---

## Performance Tips

1. **Use Specific Validators**: Prefer `IATA_AIRPORT` over `ANY` for better validation
2. **Set-Based Over Regex**: For closed sets, set-based validators are 3x faster
3. **Pre-compile Custom Sets**: Create static final validators to avoid repeated allocations

```java
// Good: Pre-compiled and reusable
private static final SegmentValidator LANGUAGES =
    SegmentValidator.setOf("en", "es", "fr", "de");

// Bad: Creates new validator every time
validators.put("lang", SegmentValidator.setOf("en", "es", "fr", "de"));
```

---

## Testing

All built-in validators are thoroughly tested:

```bash
# Test set-based validators
mvn test -Dtest=SetBasedValidatorTest

# Test built-in validators
mvn test -Dtest=BuiltInValidatorsTest

# Test all
mvn test
```

---

## Summary

| Validator | Code Length | Count | Performance | Use Case |
|-----------|-------------|-------|-------------|----------|
| `NUMERIC` | Variable | N/A | 50ns | IDs, counts |
| `UUID` | 36 chars | N/A | 50ns | Unique IDs |
| `IATA_AIRPORT` | 3 letters | 150+ | 15ns | Airport codes |
| `ICAO_AIRPORT` | 4 letters | 100+ | 15ns | Aviation codes |
| `ISO_639_1_LANGUAGE` | 2 letters | 90+ | 15ns | i18n, localization |
| `ISO_639_2_LANGUAGE` | 3 letters | 90+ | 15ns | Alternative language codes |
| `ISO_3166_COUNTRY_ALPHA2` | 2 letters | 200+ | 15ns | Country identification |
| `ISO_3166_COUNTRY_ALPHA3` | 3 letters | 200+ | 15ns | Alternative country codes |
| `ISO_4217_CURRENCY` | 3 letters | 150+ | 15ns | Pricing, payments |
| `HTTP_STATUS_CODE` | 3 digits | 60+ | 15ns | Error handling, monitoring |

**Choose set-based validators for closed sets, pattern-based for open-ended validation.**