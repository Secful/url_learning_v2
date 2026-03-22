# Path Template Trie — Design Document ("URL Learning")

## TL;DR

**What it does:** Maps concrete API paths (e.g., `/api/v2/companies/645d4369eb31790784df4dc0/posturegaps`) to parameterized templates (e.g., `/api/v2/companies/{companyId}/posturegaps`) in **~1 microsecond** using a prefix tree.

**Why it's fast:** O(k) lookup complexity where k = number of path segments (typically 3-8). No regex scanning, no linear search through thousands of patterns.

### Real-World Example: Production API Trie

**Input:** 5 concrete API requests with actual IDs (what the trie receives):

```
/api/v2/companies/645d4369eb31790784df4dc0/posturegaps
/api/v2/companies/654bc386246a8b65e779ec64/sensitive/data/grouping/parameter
/api/v2/companies/68af3287aa9ad44acdf6372f/timelinesteps/696f4b52803c910ea8a10df8
/api/v2/recon/organizations/68befe54c85efb6b109d1716/rescan
/api/v2/validation-rules/rules/6847c8c41b0000935dc44d38/toggle-activation
```

**Output:** The trie maps each concrete path to its parameterized template:
- `645d4369eb31790784df4dc0` → `{companyId}` (MongoDB ObjectID)
- `696f4b52803c910ea8a10df8` → `{stepId}` (MongoDB ObjectID)
- `68befe54c85efb6b109d1716` → `{orgId}` (MongoDB ObjectID)
- etc.

**Trie Structure:** Here's how these paths are organized internally after templates are learned:

```mermaid
graph TD
    root((ROOT))
    root --> api[api]
    api --> v2[v2]

    %% Branch 1: companies
    v2 --> companies[companies]
    companies --> companyId{"{companyId}<br/>MongoDB ObjectID"}

    companyId --> posturegaps["✓ posturegaps"]

    companyId --> sensitive[sensitive]
    sensitive --> data[data]
    data --> grouping[grouping]
    grouping --> parameter["✓ parameter"]

    companyId --> timelinesteps[timelinesteps]
    timelinesteps --> stepId{"{stepId}<br/>✓ MongoDB ObjectID"}

    %% Branch 2: recon
    v2 --> recon[recon]
    recon --> organizations[organizations]
    organizations --> orgId{"{orgId}<br/>MongoDB ObjectID"}
    orgId --> rescan["✓ rescan"]

    %% Branch 3: validation-rules
    v2 --> validationRules[validation-rules]
    validationRules --> rules[rules]
    rules --> ruleId{"{ruleId}<br/>MongoDB ObjectID"}
    ruleId --> toggleActivation["✓ toggle-activation"]

    %% Styling
    classDef leafNode fill:#90EE90,stroke:#2E7D32,stroke-width:3px
    classDef wildcardNode fill:#FFD700,stroke:#F57C00,stroke-width:2px
    classDef literalNode fill:#E3F2FD,stroke:#1976D2,stroke-width:1px
    classDef rootNode fill:#FFF3E0,stroke:#E65100,stroke-width:3px

    class posturegaps,parameter,stepId,rescan,toggleActivation leafNode
    class companyId,orgId,ruleId wildcardNode
    class api,v2,companies,sensitive,data,grouping,timelinesteps,recon,organizations,validationRules,rules literalNode
    class root rootNode
```

**Legend:**

*Node Shape (matching type):*
- **Rectangle** `[text]`: Literal segment (exact string match required)
- **Diamond** `{text}`: Wildcard segment (parameter capture with validation)
- **Circle** `((text))`: Root node

*Node Color (endpoint status):*
- 🟢 **Green with ✓**: Leaf node (endpoint storing complete template string)
- 🟡 **Yellow**: Wildcard node (intermediate, not an endpoint)
- 🔵 **Blue**: Literal node (intermediate, not an endpoint)
- 🟠 **Orange**: Root node

**Key Insights:**
- **Prefix sharing**: All 5 paths share `/api/v2`, requiring only 2 nodes for common prefix
- **Branch factor**: 3 main branches at depth 3 (`companies`, `recon`, `validation-rules`)
- **Max depth**: 8 segments for longest path (`/api/v2/companies/{id}/sensitive/data/grouping/parameter`)
- **Lookup speed**: ~0.93 µs average (1.07M lookups/second) when cache is warm

**Performance: Cold vs Warm Cache**
| Scenario | Lookup Time | Operations |
|----------|-------------|------------|
| **Cold cache** (empty trie, calls LLM) | ~1+ second | LLM inference + trie insert |
| **Warm cache** (template cached) | **~0.93 µs** | Tree walk only (8 nodes max) |
| **Speedup** | **600,000×** faster | No LLM, no regex, just pointer hops |

---

## 1. Overview

The Path Template Trie is a data structure for resolving concrete HTTP paths (e.g., `/users/jack/posts/123`) into parameterized API templates (e.g., `/users/{name}/posts/{id}`). It serves as the core lookup mechanism in the API discovery pipeline, replacing regex-based pattern matching with a deterministic, segment-level trie traversal.

The trie acts as a fast cache layer. On a cache miss, an LLM is invoked to infer the template, which is then inserted into the trie for future lookups. Over time, the trie converges toward full coverage of observed API traffic, driving LLM call frequency to near-zero.

---

## 2. Problem Statement

Given a stream of raw HTTP paths from API traffic, we need to:

1. **Resolve** each path to a known parameterized template.
2. **Distinguish** between literal segments (`users`, `settings`) and dynamic parameters (`{name}`, `{id}`).
3. **Prefer specificity** — exact literal matches should take priority over wildcard matches.
4. **Handle ambiguity** — when multiple templates could match, the system must deterministically choose the most specific one.
5. **Support runtime growth** — new templates discovered by the LLM must be insertable without downtime.

A regex-based approach (iterating N compiled patterns per incoming path) has O(N × path_length) complexity and becomes a bottleneck as the template cache grows. The trie reduces lookup to O(k) where k is the number of path segments (typically 3–6).

---

## 3. Data Structure

### 3.1 Node Structure

Each node in the trie represents a single depth level in the path hierarchy. A node contains:

| Field               | Type                        | Description                                                  |
|---------------------|-----------------------------|--------------------------------------------------------------|
| `literals`          | `Map<String, TrieNode>`     | Literal segment children. Key is the lowercased segment.     |
| `wildcardChildren`  | `List<WildcardChild>`       | Multiple wildcard children (supports different validators).  |
| `template`          | `String`                    | Non-null only at leaf nodes. The full template string.       |

Each `WildcardChild` encapsulates:
- `node` - The continuation trie node
- `def` - The WildcardDef (parameter name + validator)

This design enables **multiple validators at the same path position**, supporting API versioning and migration scenarios where different ID formats coexist.

### 3.2 Wildcard Definition

Each wildcard node carries metadata:

| Field       | Type                    | Description                                          |
|-------------|-------------------------|------------------------------------------------------|
| `paramName` | `String`                | The parameter name (e.g., `"id"`, `"name"`).         |
| `validator` | `Predicate<String>`     | Optional constraint on the segment value.            |

Built-in validators:

**Pattern-based Validators:**

| Validator | Matches | Example |
|-----------|---------|---------|
| `ANY` | Any non-empty segment | `jack`, `123`, `abc-def` |
| `NUMERIC` | Digits only (e.g., IDs) | `123`, `42`, `999` |
| `UUID` | Standard UUID format | `550e8400-e29b-41d4-a716-446655440000` |
| `MONGODB_ID` | MongoDB ObjectId (24 hex chars) | `64e7a294e854ff2eb3550075` |

**Set-based Validators (Closed Sets):**

| Validator | Type | Count | Examples |
|-----------|------|-------|----------|
| `IATA_AIRPORT` | 3-letter airport codes | 500+ | `JFK`, `LAX`, `LHR`, `CDG` |
| `ICAO_AIRPORT` | 4-letter airport codes | 300+ | `KJFK`, `EGLL`, `LFPG` |
| `ISO_639_1_LANGUAGE` | 2-letter language codes | 184 | `en`, `es`, `fr`, `de`, `zh` |
| `ISO_639_2_LANGUAGE` | 3-letter language codes | 200+ | `eng`, `spa`, `fra`, `deu` |
| `ISO_3166_COUNTRY_ALPHA2` | 2-letter country codes | 249 | `US`, `GB`, `FR`, `DE`, `CN` |
| `ISO_3166_COUNTRY_ALPHA3` | 3-letter country codes | 249 | `USA`, `GBR`, `FRA`, `DEU` |
| `ISO_4217_CURRENCY` | 3-letter currency codes | 210+ | `USD`, `EUR`, `GBP`, `JPY` |
| `HTTP_STATUS_CODE` | HTTP status codes | 60+ | `200`, `404`, `500`, `503` |
| `US_STATE_CODE` | 2-letter US state codes | 59 | `CA`, `NY`, `TX`, `DC`, `PR` |
| `DAY_OF_WEEK` | Day names (full + abbrev) | 21 | `monday`, `mon`, `friday`, `fri` |
| `MONTH_NAME` | Month names (full + abbrev) | 24 | `january`, `jan`, `december`, `dec` |

Set-based validators use HashSet for O(1) lookup (~15ns) and are case-insensitive. They're optimized for real-world closed sets like airport codes, ISO standards, and regional/temporal data.

### 3.3 Multiple Validators in a Single Path

A single path template can use **different validators for different parameters**, enabling precise validation of complex API patterns.

**Example: Flight Search API**

```java
trie.insert("/flights/{origin}/{destination}/prices/{currency}",
    Map.of(
        "origin", SegmentValidator.IATA_AIRPORT,      // JFK, LAX, LHR
        "destination", SegmentValidator.IATA_AIRPORT,  // Must be valid airport codes
        "currency", SegmentValidator.ISO_4217_CURRENCY // USD, EUR, GBP
    ));
```

Lookup behavior:
- ✅ `/flights/JFK/LAX/prices/USD` → Matches (all valid)
- ✅ `/flights/LHR/CDG/prices/EUR` → Matches (all valid)
- ❌ `/flights/XYZ/LAX/prices/USD` → **No match** (XYZ not a valid IATA code)
- ❌ `/flights/JFK/LAX/prices/ZZZ` → **No match** (ZZZ not a valid currency)

**Example: Internationalized Content API**

```java
trie.insert("/content/{lang}/{country}/news",
    Map.of(
        "lang", SegmentValidator.ISO_639_1_LANGUAGE,        // en, es, fr
        "country", SegmentValidator.ISO_3166_COUNTRY_ALPHA2 // US, GB, FR
    ));
```

Lookup behavior:
- ✅ `/content/en/US/news` → Matches (valid language + country)
- ✅ `/content/fr/FR/news` → Matches (French content for France)
- ❌ `/content/xyz/US/news` → **No match** (xyz not a valid language code)
- ❌ `/content/en/XX/news` → **No match** (XX not a valid country code)

**Example: Regional and Temporal Routing**

```java
// Regional delivery schedule API
trie.insert("/api/delivery/{state}/{day}/windows",
    Map.of(
        "state", SegmentValidator.US_STATE_CODE,  // CA, NY, TX, DC, PR
        "day", SegmentValidator.DAY_OF_WEEK       // monday, mon, fri
    ));

// Monthly analytics by region
trie.insert("/api/analytics/{state}/{month}/metrics",
    Map.of(
        "state", SegmentValidator.US_STATE_CODE,  // CA, NY, TX
        "month", SegmentValidator.MONTH_NAME      // january, jan, dec
    ));
```

Lookup behavior:
- ✅ `/api/delivery/CA/monday/windows` → Matches (California on Monday)
- ✅ `/api/delivery/NY/fri/windows` → Matches (New York on Friday)
- ✅ `/api/analytics/TX/jan/metrics` → Matches (Texas in January)
- ❌ `/api/delivery/ZZ/monday/windows` → **No match** (ZZ not a valid state)
- ❌ `/api/delivery/CA/notaday/windows` → **No match** (invalid day)

**Example: Mixed ID Types**

```java
// Company endpoint with MongoDB ID
trie.insert("/api/companies/{companyId}/config",
    Map.of("companyId", SegmentValidator.MONGODB_ID));

// Order endpoint with UUID
trie.insert("/api/orders/{orderId}/details",
    Map.of("orderId", SegmentValidator.UUID));

// User endpoint with numeric ID
trie.insert("/api/users/{userId}/profile",
    Map.of("userId", SegmentValidator.NUMERIC));
```

The trie will correctly route each path based on the ID format, preventing cross-contamination of different resource types.

### 3.4 Trie Structure Example

Given these registered templates:

```
/users/{name}
/users/{name}/posts/{id}     (id: NUMERIC)
/users/{name}/settings
/api/v1/orders/{id}          (id: UUID)
/api/v1/orders/summary
/health
```

The trie looks like:

```
ROOT
├── "users"
│   └── * {name: ANY}
│       ├── "posts"
│       │   └── * {id: NUMERIC}  → "/users/{name}/posts/{id}"
│       ├── "settings"           → "/users/{name}/settings"
│       └── (leaf)               → "/users/{name}"
├── "api"
│   └── "v1"
│       └── "orders"
│           ├── "summary"        → "/api/v1/orders/summary"
│           └── * {id: UUID}     → "/api/v1/orders/{id}"
└── "health"                     → "/health"
```

---

## 4. Insert Flow

Insertion is triggered when the LLM returns a new template. The template string is parsed segment by segment; literal segments create literal children, `{param}` segments create wildcard children.

### 4.1 Algorithm

```
function insert(template, validators):
    segments = split(template, "/")
    node = root
    for each segment in segments:
        if segment is "{param}":
            paramName = extract(segment)
            validator = validators.get(paramName) or ANY

            // Check if wildcard with this validator already exists
            existingWildcard = node.findWildcardChild(validator)
            if existingWildcard != null:
                node = existingWildcard.getNode()  // Reuse existing path
            else:
                // Create new wildcard child with this validator
                newNode = new TrieNode()
                wildcardChild = new WildcardChild(newNode, WildcardDef(paramName, validator))
                node.addWildcardChild(wildcardChild)
                node = newNode
        else:
            node = node.literals.computeIfAbsent(segment)
    node.template = template
```

### 4.2 Insert Flow Diagram

```mermaid
flowchart TD
    A[Receive template string] --> B[Split by '/']
    B --> C[Start at ROOT node]
    C --> D{Next segment?}
    D -- No more segments --> E[Mark current node as leaf<br/>Store template string]
    D -- Yes -->     F{"Is segment<br/>a wildcard #lbrace;param#rbrace; ?"}
    F -- Yes --> G{Wildcard child<br/>with this validator<br/>exists?}
    G -- No --> H["Create new WildcardChild<br/>with new TrieNode and<br/>WildcardDef paramName + validator<br/>Add to wildcardChildren list"]
    G -- Yes --> I["Reuse existing<br/>wildcard child with<br/>matching validator"]
    H --> J[Move to wildcard child node]
    I --> J
    J --> D
    F -- No --> K{Literal child<br/>exists for segment?}
    K -- No --> L[Create new literal<br/>child node]
    K -- Yes --> M[Reuse existing<br/>literal child]
    L --> N[Move to literal child]
    M --> N
    N --> D

    style A fill:#2d3748,stroke:#4a5568,color:#e2e8f0
    style E fill:#276749,stroke:#38a169,color:#e2e8f0
    style F fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style K fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style G fill:#744210,stroke:#d69e2e,color:#e2e8f0
```

### 4.3 Insert Example Walkthrough

Inserting `/users/{name}/posts/{id}` with `{id: NUMERIC}`:

| Step | Segment    | Action                              | Current Node         |
|------|------------|-------------------------------------|----------------------|
| 1    | `users`    | Create/find literal child "users"   | ROOT → "users"       |
| 2    | `{name}`   | Create wildcard child {name: ANY}   | "users" → * {name}   |
| 3    | `posts`    | Create/find literal child "posts"   | * {name} → "posts"   |
| 4    | `{id}`     | Create wildcard child {id: NUMERIC} | "posts" → * {id}     |
| done |            | Mark leaf, store template           | * {id} ✓             |

---

## 5. Lookup Flow

Lookup takes a concrete path and traverses the trie depth-first, **always preferring literal matches over wildcard matches** at each level. If a branch leads to a dead end, the algorithm backtracks and tries the wildcard alternative.

### 5.1 Algorithm

```
function lookup(path):
    segments = split(path, "/")
    params = {}
    return doLookup(root, segments, depth=0, params)

function doLookup(node, segments, depth, params):
    if depth == segments.length:
        return node.template != null ? Match(node.template, params) : null

    segment = segments[depth]

    // Priority 1: Try literal match
    literalChild = node.literals.get(segment)
    if literalChild != null:
        result = doLookup(literalChild, segments, depth+1, params)
        if result != null: return result

    // Priority 2: Try all wildcard children
    for each wildcardChild in node.wildcardChildren:
        wildcardDef = wildcardChild.getDef()
        if wildcardDef.validator.test(segment):
            params.put(wildcardDef.paramName, segment)
            result = doLookup(wildcardChild.getNode(), segments, depth+1, params)
            if result != null: return result
            params.remove(wildcardDef.paramName)  // backtrack

    return null
```

### 5.2 Lookup Flow Diagram

```mermaid
flowchart TD
    A[Receive concrete path] --> B[Split by '/']
    B --> C["Start at ROOT, depth=0"]
    C --> D{depth == <br/>segment count?}
    D -- Yes --> E{Current node<br/>has template?}
    E -- Yes --> F[Return MatchResult<br/>template + captured params]
    E -- No --> G[Return null<br/>no match at this depth]
    D -- No --> H["Get segment at current depth"]
    H --> I{Literal child exists<br/>for this segment?}
    I -- Yes --> J[Recurse into literal child<br/>depth + 1]
    J --> K{Recursion<br/>returned match?}
    K -- Yes --> F
    K -- No --> L{Any wildcard children<br/>exist?}
    I -- No --> L
    L -- No --> G
    L -- Yes --> M["Try each wildcard child in order"]
    M --> N{Current wildcard's<br/>validator accepts<br/>this segment?}
    N -- No --> O{More wildcard<br/>children to try?}
    O -- Yes --> M
    O -- No --> G
    N -- Yes --> P["Capture: params#lbrace;paramName#rbrace; = segment"]
    P --> Q[Recurse into wildcard child<br/>depth + 1]
    Q --> R{Recursion<br/>returned match?}
    R -- Yes --> F
    R -- No --> S[Remove param from map<br/>BACKTRACK]
    S --> O

    style A fill:#2d3748,stroke:#4a5568,color:#e2e8f0
    style F fill:#276749,stroke:#38a169,color:#e2e8f0
    style G fill:#742a2a,stroke:#e53e3e,color:#e2e8f0
    style I fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style L fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style M fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style E fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style K fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style P fill:#744210,stroke:#d69e2e,color:#e2e8f0
```

### 5.3 Lookup Example Walkthrough

Path: `/api/v1/orders/summary`

| Depth | Segment    | Literal child? | Wildcard child? | Action                     |
|-------|------------|----------------|-----------------|----------------------------|
| 0     | `api`      | ✅ "api"       | —               | Follow literal             |
| 1     | `v1`       | ✅ "v1"        | —               | Follow literal             |
| 2     | `orders`   | ✅ "orders"    | —               | Follow literal             |
| 3     | `summary`  | ✅ "summary"   | ✅ * {id: UUID} | Literal wins → follow it   |
| done  |            |                |                 | → `/api/v1/orders/summary` |

Path: `/api/v1/orders/550e8400-e29b-41d4-a716-446655440000`

| Depth | Segment           | Literal child? | Wildcard child? | Action                         |
|-------|-------------------|----------------|-----------------|--------------------------------|
| 0     | `api`             | ✅ "api"       | —               | Follow literal                 |
| 1     | `v1`              | ✅ "v1"        | —               | Follow literal                 |
| 2     | `orders`          | ✅ "orders"    | —               | Follow literal                 |
| 3     | `550e8400-e2...`  | ❌             | ✅ * {id: UUID} | No literal → try wildcard      |
|       |                   |                | UUID valid? ✅  | Capture id, follow wildcard    |
| done  |                   |                |                 | → `/api/v1/orders/{id}`        |

Path: `/api/v1/orders/not-a-uuid`

| Depth | Segment        | Literal child? | Wildcard child? | Action                            |
|-------|----------------|----------------|-----------------|-----------------------------------|
| 0–2   | (same as above)| ✅             | —               | Follow literals                   |
| 3     | `not-a-uuid`   | ❌             | ✅ * {id: UUID} | No literal → try wildcard         |
|       |                |                | UUID valid? ❌  | Validator rejects → **NO MATCH**  |

---

## 6. End-to-End System Flow

```mermaid
flowchart LR
    A[HTTP Traffic] --> B[Extract Path]
    B --> C[Trie Lookup]
    C --> D{Match?}
    D -- Hit --> E[Return Template]
    D -- Miss --> F[LLM Inference]
    F --> G[LLM returns template]
    G --> H[Insert into Trie]
    H --> E

    style D fill:#744210,stroke:#d69e2e,color:#e2e8f0
    style E fill:#276749,stroke:#38a169,color:#e2e8f0
    style F fill:#553c9a,stroke:#805ad5,color:#e2e8f0
```

---

## 7. Concurrency Model

The trie is designed for a **read-heavy workload**: the vast majority of operations are lookups, with occasional inserts when the LLM discovers a new template.

| Operation | Lock Type  | Blocking Behavior                    |
|-----------|------------|--------------------------------------|
| Lookup    | Read lock  | Concurrent with other reads          |
| Insert    | Write lock | Exclusive — blocks reads and writes  |
| Remove    | Write lock | Exclusive — blocks reads and writes  |
| List      | Read lock  | Concurrent with other reads          |

The `ConcurrentHashMap` for literal children provides additional safety for concurrent read access to the literal map itself.

---

## 8. Performance Characteristics

| Metric            | Value                          | Notes                                   |
|-------------------|--------------------------------|-----------------------------------------|
| Lookup time       | O(k)                           | k = number of path segments (3–6 typical) |
| Insert time       | O(k)                           | One node creation per segment            |
| Memory per template | O(k) nodes                   | Shared prefixes are deduplicated         |
| Memory for 10K templates | ~40K nodes            | Assuming avg 4 segments/template         |
| Backtracking worst case | O(2^k)                  | Only with many overlapping wildcards; negligible in practice for REST APIs |

### Comparison with Regex Approach

| Approach            | Lookup Complexity       | Maintenance      |
|---------------------|-------------------------|------------------|
| Iterate N regexes   | O(N × path_length)      | Recompile on add |
| Single combined DFA | O(path_length)          | Recompile on add |
| **Segment trie**    | **O(k), k = segments**  | **O(k) insert**  |

---

## 9. Capabilities and Limitations

### ✅ Current Capabilities

**Multiple validators at the same position** — Each trie node supports multiple wildcard children with different validators. This enables:
- **API versioning**: Different API versions can use different ID formats at the same path position
- **Migration scenarios**: Legacy and new ID formats coexist during transitions
- **Multi-format support**: Same endpoint accepts different ID types simultaneously

Example:
```java
// Both validators at the same position
trie.insert("/api/users/{id}/profile", Map.of("id", NUMERIC));
trie.insert("/api/users/{id}/profile", Map.of("id", UUID));

// Both formats are valid:
lookup("/api/users/123/profile")                           → ✓ matches NUMERIC
lookup("/api/users/550e8400-e29b-41d4-a716-.../profile")   → ✓ matches UUID
```

Performance: Each wildcard validator is tried in order until one matches. Impact is negligible (~0.01 µs per additional validator), maintaining sub-microsecond lookup times.

### ⚠️ Current Limitations

**Multi-segment wildcards** — Each wildcard matches exactly one path segment. Paths like `/files/{filepath}` where `filepath` spans multiple segments (e.g., `a/b/c.txt`) are not supported. Such patterns require a greedy wildcard that consumes remaining segments. Current recommendation: handle these through LLM inference on cache miss.

**In-memory only** — The trie persists only in memory. On application restart, the trie must be rebuilt from stored templates. The `listTemplates()` method enables persistence: serialize all templates to storage and re-insert on startup.

**No conflict detection** — Inserting overlapping templates (e.g., `/users/{name}` and `/users/{id}` both with ANY validator) creates ambiguous paths. The trie accepts both without warning. The first matching validator during lookup determines which template is used.

---

## 10. Trie Health Monitoring & Fixing Loop

### 10.1 The Problem: LLM Inference Drift

The trie acts as a cache for LLM-inferred templates. However, because each LLM invocation starts with an **empty context** (no visibility into existing templates), the LLM can produce inconsistent results over time:

**Scenario**: Three sequential API requests arrive
```
Request 1: /users/john/profile   (trie empty)
  → LLM infers: /users/john/profile (treats "john" as literal)
  → Trie now contains: /users/john/profile

Request 2: /users/mary/profile   (trie has john's path)
  → LLM infers: /users/mary/profile (treats "mary" as literal)
  → Trie now contains: /users/john/profile, /users/mary/profile

Request 3: /users/alice/profile  (trie has john, mary)
  → LLM infers: /users/alice/profile (treats "alice" as literal)
  → Trie now contains 3 separate templates for the same pattern!
```

**Problem**: The trie accumulates redundant, fragmented templates. What should be one template `/users/{name}/profile` becomes dozens or hundreds of literals: `/users/john/...`, `/users/mary/...`, etc.

**Impact**:
- Unbounded trie growth (one template per unique username)
- LLM invoked repeatedly for the same pattern (cache miss for every new user)
- Inconsistent routing behavior
- Memory waste

### 10.2 Solution: Periodic Fixing Loop

A background process periodically analyzes the trie, detects LLM inference issues, and **consolidates** or **corrects** problematic templates.

```mermaid
flowchart LR
    A[API Traffic] --> B[PathResolver]
    B --> C{Trie<br/>Cache Hit?}
    C -- Hit --> D[Return Template]
    C -- Miss --> E[LLM Inference]
    E --> F[Insert Template]
    F --> D

    G[Fixing Loop<br/>Periodic] -.-> H[Analyze Trie]
    H -.-> I{Issues<br/>Detected?}
    I -- Yes --> J[Generate Fixes]
    J -.-> K[Apply Fixes]
    K -.-> C
    I -- No --> L[Sleep]
    L -.-> H

    style E fill:#553c9a,stroke:#805ad5,color:#e2e8f0
    style G fill:#c05621,stroke:#dd6b20,color:#e2e8f0
    style K fill:#276749,stroke:#38a169,color:#e2e8f0
```

### 10.3 Detection Phase: Identifying Issues

The fixing loop analyzes templates from `listTemplates()` to detect five categories of problems:

#### Issue Type 1: Under-Parameterization (Template Fragmentation)

**Detection Algorithm**: Structural Similarity Clustering

```
For each depth group (templates with same segment count):
  1. Parse templates into segment arrays
  2. Compare pairs: count positions where segments differ
  3. Cluster templates differing at exactly ONE position
  4. If cluster size ≥ threshold (e.g., 3):
     → Flag as under-parameterization

Confidence Heuristics:
  - Check if differing segment values are API keywords → Lower confidence
  - Check if parameterized version already exists → Raise to CRITICAL
  - Analyze value diversity (john, mary, alice vs v1, v1, v1) → Higher if diverse
```

**Example Detection**:
```
Cluster found:
  Pattern: [users, ?, profile]
  Members: /users/john/profile, /users/mary/profile, /users/alice/profile (×47)

Analysis:
  - Position 1 varies: john, mary, alice, robert, sarah... (diverse)
  - Not API keywords: ✗
  - Parameterized version exists: /users/{name}/settings (CRITICAL - inconsistency!)

Recommendation: Consolidate 47 templates → /users/{name}/profile
```

#### Issue Type 2: Inconsistent Parameter Naming

**Detection Algorithm**: Position-Based Name Analysis

```
For templates with similar structure:
  1. Group by literal prefix pattern
  2. Extract parameter names at each position
  3. If same position has multiple names (userId vs user_id vs id):
     → Flag as naming inconsistency

Confidence:
  - If all same validator type → Higher confidence (likely same concept)
  - If different validators → Lower confidence (might be different things)
```

**Example**:
```
Templates with prefix /api/v1/users:
  Position 3 parameter names:
    - userId (15 templates)
    - user_id (8 templates)
    - id (3 templates)

Recommendation: Standardize on most common name: userId
```

#### Issue Type 3: Over-Parameterization

**Detection Algorithm**: Value Frequency Analysis (requires runtime data)

```
For each parameterized segment:
  1. Track actual values captured during lookups
  2. Calculate unique value count and distribution
  3. If unique values ≤ threshold (e.g., 2) over many requests:
     → Flag as over-parameterization

Example:
  Template: /api/{version}/users
  Values seen: v1, v1, v1, v1, v1 (1000×), v2 (2×)
  → Only 2 unique values across 1002 requests

Recommendation: Consider literal /api/v1/users and /api/v2/users
```

**Challenge**: Requires **runtime instrumentation** to track parameter values.

#### Issue Type 4: Wrong Validator

**Detection Algorithm**: Format Pattern Analysis (requires runtime data)

```
For parameters using ANY validator:
  1. Collect sample of actual values matched
  2. Analyze format patterns (UUID format? 24 hex chars? Numeric?)
  3. If 95%+ match a specific validator pattern:
     → Recommend switching to that validator

Example:
  Parameter: {companyId} with ANY validator
  Values: 64e7a294e854ff2eb3550075, 645d4369eb31790784df4dc0, ...
  Analysis: 100% are 24-char hex (MongoDB ObjectID format)

Recommendation: Change validator to MONGODB_ID
```

#### Issue Type 5: API Version Pattern Issues

**Detection Algorithm**: Regex-Based Scan

```
For each template:
  1. Scan for parameterized version segments: {v1}, {v2}, {version}
  2. Check position: if NOT last segment → flag
  3. Recommend: Make literal (v1, v2) or keep as {version} if last

Example:
  ❌ /api/{version}/users  → Should be /api/v1/users (literal)
  ✓  /api/{version}        → OK (last segment, truly dynamic)
```

### 10.4 Fixing Phase: Strategies for Correction

Once issues are detected, the fixing loop must decide **how to fix** them. Multiple strategies exist, each with trade-offs.

#### Strategy 1: Template Consolidation (For Under-Parameterization)

**Algorithm**: Remove + Re-insert
```
Given cluster: [/users/john/profile, /users/mary/profile, /users/alice/profile]

Step 1: Generate consolidated template
  Pattern: /users/{name}/profile
  Validator: Infer from values (if all look like strings → ANY)

Step 2: Remove old templates from trie
  trie.remove("/users/john/profile")
  trie.remove("/users/mary/profile")
  trie.remove("/users/alice/profile")

Step 3: Insert consolidated template
  trie.insert("/users/{name}/profile", Map.of("name", ANY))

Result: 47 templates → 1 template
```

**Validation**:
```
Test that old paths still resolve:
  lookup("/users/john/profile") → Should match /users/{name}/profile ✓
  lookup("/users/mary/profile") → Should match /users/{name}/profile ✓
```

**Risk**: If the differing segment was intentionally literal, consolidation breaks semantics.

**Mitigation**: Use confidence thresholds. Only auto-fix HIGH confidence issues (e.g., 10+ cluster members). Flag MEDIUM confidence for human review.

---

#### Strategy 2: LLM Re-Inference (For Validation)

**Algorithm**: Ask LLM to re-infer with context
```
Given problematic cluster, pick a sample path:
  Sample: /users/john/profile

Step 1: Re-invoke LLM with **enriched prompt**
  Prompt: "Path: /users/john/profile
           Context: Similar paths exist: /users/mary/profile, /users/alice/profile
           Are john, mary, alice dynamic parameters or literal endpoints?"

Step 2: Compare LLM result to existing templates
  If LLM returns /users/{name}/profile → Confirms consolidation
  If LLM returns /users/john/profile → Keep as-is (false positive)

Step 3: Apply fix if LLM confirms issue
```

**Benefit**: Uses LLM's intelligence to validate fixes.

**Cost**: Expensive (LLM call per issue). Use selectively for CRITICAL or MEDIUM confidence issues.

---

#### Strategy 3: Validator Upgrade (For Wrong Validator)

**Algorithm**: Replace validator in-place
```
Given: /api/companies/{companyId} with ANY validator
Analysis: All values are MongoDB ObjectIDs

Step 1: Remove existing template
  trie.remove("/api/companies/{companyId}")

Step 2: Re-insert with correct validator
  trie.insert("/api/companies/{companyId}",
    Map.of("companyId", SegmentValidator.MONGODB_ID))

Result: More precise validation, faster matching
```

**Validation**:
```
Test with sample values:
  lookup("/api/companies/64e7a294e854ff2eb3550075") → Still matches ✓
  lookup("/api/companies/not-a-real-id") → Now correctly rejects ✓
```

---

#### Strategy 4: Parameter Renaming (For Inconsistent Naming)

**Algorithm**: Standardize parameter names
```
Given inconsistency:
  /api/v1/users/{userId}/posts
  /api/v1/users/{user_id}/settings
  /api/v1/users/{id}/profile

Step 1: Choose canonical name (most common or follow convention)
  Canonical: userId (appears 15 times vs 8 vs 3)

Step 2: Re-insert templates with standardized name
  Remove: /api/v1/users/{user_id}/settings
  Insert: /api/v1/users/{userId}/settings

Result: Consistent naming across API surface
```

**Impact**: Improves API documentation, parameter extraction consistency.

**Risk**: LOW (parameter name doesn't affect routing, only captured params map).

---

### 10.5 Fixing Loop Architecture

```mermaid
flowchart TD
    A[Scheduled Trigger<br/>Every N minutes or<br/>After M new templates] --> B[Acquire Read Lock<br/>listTemplates]
    B --> C[Detection Phase<br/>Run all analyzers]
    C --> D{Issues<br/>Found?}
    D -- No --> E[Log: Trie Healthy<br/>Sleep until next cycle]
    D -- Yes --> F[Generate Fix Proposals<br/>with confidence scores]
    F --> G{Confidence<br/>Level?}
    G -- HIGH --> H[Auto-Fix Queue]
    G -- MEDIUM --> I[Human Review Queue]
    G -- LOW --> J[Log Only<br/>No Action]
    H --> K[Validation Phase<br/>Test fixes on sample paths]
    K --> L{Validation<br/>Pass?}
    L -- Yes --> M[Acquire Write Lock<br/>Apply fixes to trie]
    L -- No --> N[Log: Fix Failed<br/>Move to review queue]
    M --> O[Post-Fix Validation<br/>Ensure no regressions]
    O --> P[Log: Fixes Applied<br/>Report metrics]
    I --> Q[Notify Admins<br/>Dashboard or Alert]

    style A fill:#c05621,stroke:#dd6b20,color:#e2e8f0
    style M fill:#276749,stroke:#38a169,color:#e2e8f0
    style N fill:#742a2a,stroke:#e53e3e,color:#e2e8f0
    style Q fill:#744210,stroke:#d69e2e,color:#e2e8f0
```

### 10.6 Configuration & Tuning

**Trigger Conditions**:
- **Time-based**: Every N minutes (e.g., hourly during low traffic)
- **Event-based**: After M new templates learned (e.g., every 100 templates)
- **Manual**: Admin-triggered via API or CLI

**Confidence Thresholds**:
```
HIGH confidence (auto-fix):
  - Under-parameterization with 10+ cluster members
  - Inconsistent naming with 20+ templates
  - Wrong validator with 100+ confirmed samples

MEDIUM confidence (human review):
  - Under-parameterization with 3-9 cluster members
  - Over-parameterization with suspicious patterns
  - API version issues

LOW confidence (log only):
  - Cluster size = 2
  - Ambiguous patterns
```

**Safety Mechanisms**:
- **Dry-run mode**: Preview fixes without applying
- **Rollback**: Store pre-fix trie snapshot, allow instant revert
- **Rate limiting**: Max N fixes per cycle (avoid cascading changes)
- **Validation gate**: Test fixes on sample paths before applying

### 10.7 Metrics & Observability

**Health Metrics**:
```
Trie Health Score (0-100):
  - Template count: lower is better (indicates consolidation)
  - Cluster count: 0 clusters = 100 score
  - Naming consistency: % templates following naming convention
  - Validator coverage: % parameters with specific validators (not ANY)

Example:
  Score: 87/100
  - Total templates: 1,247 (good)
  - Under-parameterization clusters: 3 (minor issue)
  - Naming consistency: 94% (excellent)
  - Validator coverage: 78% (good)
```

**Fixing Loop Metrics**:
```
Per-cycle metrics:
  - Issues detected: 15
  - Auto-fixed: 8
  - Pending review: 5
  - Failed validation: 2
  - Templates consolidated: 47 → 5 (89% reduction)
  - Trie size reduction: 1.2 MB → 312 KB
```

### 10.8 Advanced: Learning from Fixes

**Feedback Loop to LLM Prompt**:

The fixing loop can identify **systematic** LLM inference patterns and feed them back to improve the prompt.

**Example**: If fixing loop consistently finds MongoDB ObjectIDs being validated as ANY:
```
Detection: 15 fixes applied this week, all upgrading ANY → MONGODB_ID

Action: Update LLM system prompt:
  Add rule: "If segment is 24 hex characters (e.g., 64e7a294e854ff2eb3550075),
             use MONGODB_ID validator, not ANY"

Result: Future LLM inferences use correct validator from the start
```

**Feedback Categories**:
- Common parameter naming patterns → Add to style guide in prompt
- Frequently consolidated patterns → Add examples to prompt
- API version handling → Refine version detection rules

**Meta-Optimization**: The system learns and improves its own prompts over time based on real-world fixing patterns.

---

## 11. HTTP Server

An embedded Jetty 12 server exposes the `PathTemplateTrie` over HTTP, allowing external clients to interact with the trie via a REST API.

### Prerequisites

- **Java 21** or later
- **Maven 3.8** or later

### Starting the Server

```bash
# Default port 8080
./run_server.sh

# Custom port
./run_server.sh 9090
```

Or run directly:
```bash
mvn package -DskipTests
java -cp target/url_learning_v2-1.0-SNAPSHOT.jar salt.security.Main server 8080
```

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/api/v1/trie/lookup` | Lookup a concrete path |
| `POST` | `/api/v1/trie/templates` | Insert a template |
| `DELETE` | `/api/v1/trie/templates` | Remove a template |
| `GET` | `/api/v1/trie/templates` | List all templates |

### curl Examples

```bash
# Health check
curl localhost:8080/health

# Insert a template with validators
curl -X POST localhost:8080/api/v1/trie/templates \
  -H 'Content-Type: application/json' \
  -d '{"template":"/users/{id}/posts/{postId}","validators":{"id":"NUMERIC","postId":"NUMERIC"}}'

# Lookup a path
curl -X POST localhost:8080/api/v1/trie/lookup \
  -H 'Content-Type: application/json' \
  -d '{"path":"/users/42/posts/123"}'

# List all templates
curl localhost:8080/api/v1/trie/templates

# Remove a template
curl -X DELETE localhost:8080/api/v1/trie/templates \
  -H 'Content-Type: application/json' \
  -d '{"template":"/users/{id}/posts/{postId}"}'
```

See `openapi.yaml` for the full OpenAPI 3.0 specification.