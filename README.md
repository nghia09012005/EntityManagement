# SOC Entity Management

Nền tảng SOC thu thập log từ nhiều nguồn, chuẩn hóa, làm giàu dữ liệu qua pipeline Kafka, và lưu thành **entity graph** trong Neo4j để phân tích quan hệ tấn công.

**Tech stack:** Java 21 · Spring Boot 3.5 · React + Vite · Kafka · MongoDB · Neo4j · Redis · MinIO

---

## Kiến trúc hệ thống

```
  Browser / API Client
         │
         │  POST /api/v1/ingest   (text/plain — free text)
         │  POST /api/files/upload (multipart — log file)
         ▼
  ┌─────────────────┐
  │  Ingestion API  │  → lưu raw log vào MongoDB (audit_logs)
  └────────┬────────┘
           │ publish
           ▼
   Kafka: raw-logs
           │
           ▼
  ┌────────────────────────────────────────────────────┐
  │  ParserWorker  (soc-parser-group)                  │
  │                                                    │
  │  JSON structured  →  detect format → parse         │
  │  Free text        →  LLM (Gemini → Groq → Mock)   │
  │                       → parse → normalize          │
  │  lỗi → Kafka: dead-letter-queue                   │
  └────────────────────┬───────────────────────────────┘
                       │ publish
                       ▼
           Kafka: normalized-events
                  ┌────┴────┐
                  ▼         ▼
  ┌─────────────────┐   ┌──────────────────────────────────┐
  │ EnrichmentWorker│   │ GraphWorker  (soc-graph-group)   │
  │ (soc-enrich..)  │   │                                  │
  │ IP  → GeoIP +   │   │ GraphEntityService → MERGE       │
  │      AbuseIPDB  │   │   entities + relationships       │
  │      + OTX      │   │   vào Neo4j (chỉ identity,       │
  │ Hash → VT       │   │   KHÔNG lưu enrichment fields)   │
  │                 │   │ lỗi → dead-letter-queue          │
  │ → lưu enrichment│   └──────────────────────────────────┘
  │   vào MongoDB   │
  │   (audit_logs)  │
  │ lỗi → DLQ      │
  └─────────────────┘

  UI muốn hiển thị enrichment của entity:
    GET /api/enrichment/entity?type=ip&value=1.2.3.4
    → query MongoDB audit_logs → trả về {geo, ipIntel, malware, ...}
    (tách biệt: Neo4j = graph topology, MongoDB = enrichment data)

  Background job (mỗi ~2 phút):
  GraphDeduplicationService → SAME_AS links + MongoDB: graph_dedup_log
```

---

## Kafka Topics

| Topic               | Producer          | Consumer                          |
|---------------------|-------------------|-----------------------------------|
| `raw-logs`          | Ingestion API     | ParserWorker                      |
| `normalized-events` | ParserWorker      | EnrichmentWorker + **GraphWorker** |
| `dead-letter-queue` | bất kỳ worker nào | DlqWorker → MongoDB `dlq_events`  |

---

## Entity Types & Properties

| Label           | ID Property    | Properties khác (trong Neo4j)                           |
|-----------------|----------------|---------------------------------------------------------|
| `User`          | `username`     | —                                                       |
| `Host`          | `hostname`     | —                                                       |
| `IP`            | `address`      | — *(enrichment lấy từ MongoDB)*                         |
| `Domain`        | `name`         | —                                                       |
| `FileHash`      | `hash`         | — *(enrichment lấy từ MongoDB)*                         |
| `Url`           | `url`          | —                                                       |
| `Process`       | `name`         | `path`, `commandLine`                                   |
| `CloudResource` | `resourceId`   | —                                                       |
| `Email`         | `address`      | —                                                       |
| `Cve`           | `cveId`        | —                                                       |

> **Enrichment data** (country, ASN, abuseScore, threatLevel, verdict, family…) được lưu riêng trong MongoDB collection `audit_logs` (field `enrichment`). UI query qua `GET /api/enrichment/entity?type=ip&value=...` thay vì đọc từ Neo4j node properties.

### Normalization (EntityNormalizer)

| Loại      | Quy tắc                                                        |
|-----------|----------------------------------------------------------------|
| username  | lowercase; bỏ `DOMAIN\` prefix                                 |
| ip        | bỏ `::ffff:` IPv4-mapped IPv6 prefix                           |
| hash      | lowercase, trim                                                |
| hostname  | lowercase, bỏ trailing `.`                                     |
| domain    | lowercase, bỏ trailing `.`                                     |
| url       | lowercase, bỏ trailing `/`                                     |
| email     | lowercase, trim                                                |
| cveId     | uppercase canonical (`CVE-YYYY-NNNNN`)                         |
| processName | basename extraction (`C:\...\cmd.exe` → `cmd.exe`), lowercase |

---

## Relationships

| Relationship       | From          | To              | Nguồn event                              |
|--------------------|---------------|-----------------|------------------------------------------|
| `LOGGED_IN_TO`     | User          | Host            | AuthenticationEvent                      |
| `AUTHENTICATED_TO` | IP            | Host            | AuthenticationEvent (khi có IP)          |
| `EXECUTED_ON`      | FileHash      | Host            | ProcessEvent                             |
| `EXECUTED_ON`      | Process       | Host            | ProcessEvent / AlertEvent                |
| `HASH_OF`          | FileHash      | Process         | ProcessEvent (khi có cả hash và name)    |
| `CONNECTED_TO`     | IP            | IP              | NetworkEvent                             |
| `RESOLVES_TO`      | IP            | Domain          | NetworkEvent / AlertEvent                |
| `ALERTED_FROM`     | User          | IP              | AlertEvent                               |
| `TARGETED_AT`      | IP            | Host            | AlertEvent                               |
| `DETECTED_ON`      | FileHash      | IP              | AlertEvent                               |
| `ACCESSED`         | IP            | Url             | AlertEvent                               |
| `ACCESSED`         | User          | CloudResource   | AlertEvent                               |
| `HAS_EMAIL`        | User          | Email           | AlertEvent                               |
| `AFFECTS`          | Cve           | Host            | AlertEvent                               |
| `SAME_AS`          | Node          | Node            | GraphDeduplicationService (background)   |

Tất cả relationship đều có: `firstSeen`, `lastSeen`, `count`, `firstEventId`, `lastEventId`.

---

## Entity Deduplication (SAME_AS)

`GraphDeduplicationService` chạy định kỳ (mặc định 2 phút), tạo `SAME_AS` link giữa các entity có thể là cùng một thực thể:

| Rule              | Ví dụ                                         | Confidence |
|-------------------|-----------------------------------------------|------------|
| `email_prefix`    | `nghia` ↔ `nghia@company.vn`                  | 85%        |
| `fqdn_shortname`  | `WIN-PC01` ↔ `WIN-PC01.corp.local`            | 80%        |

Mỗi link mới được ghi vào MongoDB collection `graph_dedup_log`.
---

## Parsers

| Parser               | EventType      | Input format                                    |
|----------------------|----------------|-------------------------------------------------|
| WindowsEventParser   | AUTHENTICATION | Windows Event Log JSON (event_id 4624/4625)     |
| NetworkEventParser   | NETWORK        | Firewall / flow JSON                            |
| ProcessEventParser   | PROCESS        | Sysmon / EDR process event JSON                 |
| AlertEventParser     | ALERT          | Generic alert JSON                              |
| LLM fallback         | bất kỳ         | Free text — Gemini → Groq → Mock (offline safe) |

---

## Enrichment Sources

| Nguồn      | Đối tượng    | Dữ liệu thu được                            |
|------------|--------------|---------------------------------------------|
| GeoIP      | IP address   | country, city, ASN                          |
| AbuseIPDB  | IP address   | abuseScore (0–100), isMalicious             |
| OTX        | IP / Domain  | threatLevel (NONE/LOW/MEDIUM/HIGH/CRITICAL) |
| VirusTotal | SHA-256 hash | verdict (MALICIOUS/CLEAN/UNKNOWN), family   |

---

## MongoDB Collections

| Collection        | Nội dung                                             |
|-------------------|------------------------------------------------------|
| `audit_logs`      | Raw log gốc trước khi parse, lưu ngay khi nhận vào  |
| `graph_dedup_log` | Log mỗi SAME_AS link mới được tạo bởi dedup job     |

---

## Project Structure

```
src/main/java/com/viettelDigitalTalent/EntitiyManagement/
├── ingestion/              # Tiếp nhận log (REST API, file upload)
│   ├── controller/
│   ├── dto/
│   └── service/
│
├── queue/                  # Kafka pipeline
│   ├── config/             # KafkaTopicConstants, KafkaTopicsConfig
│   ├── publisher/          # DeadLetterPublisher
│   └── worker/             # ParserWorker, EnrichmentWorker, GraphWorker
│
├── parser/                 # Chuẩn hóa log thô → BaseEvent
│   ├── core/               # EventParser interface + dispatcher
│   ├── windows/            # Windows Event Log parser
│   ├── network/            # Network / firewall parser
│   ├── process/            # Process / EDR parser
│   └── alert/              # Generic alert parser
│
├── normalize/              # Event data models
│   ├── base/               # BaseEvent (@JsonTypeInfo polymorphism)
│   ├── event/              # AuthenticationEvent, ProcessEvent, NetworkEvent
│   └── alert/              # AlertEvent (10 target fields)
│
├── enrichment/             # Làm giàu dữ liệu
│   ├── core/               # EnrichmentService dispatcher
│   ├── geoip/              # GeoIP lookup (Redis cache)
│   ├── ipintel/            # AbuseIPDB + OTX
│   └── threatintel/        # VirusTotal
│
├── graph/                  # Neo4j entity graph
│   ├── controller/         # GraphController (REST API)
│   ├── service/            # GraphEntityService, GraphQueryService
│   │                       # GraphDeduplicationService, EntityNormalizer
│   └── dto/                # NodeDto, EdgeDto, GraphResponse, PathResponse
│
├── llm/                    # LLM fallback chain
│   ├── core/               # LLM client interface + factory
│   └── llmFilter/          # Gemini / Groq / Mock clients
│
├── storage/
│   ├── mongodb/            # AuditLog, GraphDedupLog documents
│   └── repository/         # MongoRepository interfaces
│
├── detection/              # Rule-based detection engine
├── management/             # CRUD API (rules, assets)
└── common/                 # Exception handling, utils

frontend/src/
├── pages/
│   ├── HomePage.jsx         # Danh sách entity + Upload panel
│   ├── EntityDetailPage.jsx # Chi tiết node + Enrichment + Relationships
│   └── GraphPage.jsx        # GraphView + PathFinder
├── components/
│   ├── EntityBadge.jsx      # Color-coded entity badge (10 types)
│   ├── GraphView.jsx        # Canvas graph visualization
│   ├── PathFinder.jsx       # Attack path search UI
│   └── UploadPanel.jsx      # File upload + Free text alert input
└── api.js                   # Fetch wrappers cho tất cả backend API
```

---

## Cách chạy

### Yêu cầu

| Tool                  | Version  |
|-----------------------|----------|
| Java                  | 21+      |
| Maven                 | 3.8+     |
| Node.js               | 18+      |
| Docker & Docker Compose | latest |

### 1 — Khởi động Infrastructure

```bash
docker-compose up -d
```

| Service    | Port(s)        | Mục đích                    |
|------------|----------------|-----------------------------|
| Kafka      | 9092           | Message streaming           |
| Zookeeper  | 2181           | Kafka coordinator           |
| MongoDB    | 27017          | Raw logs + dedup log        |
| Redis      | 6379           | Enrichment cache            |
| MinIO      | 9000 / 9001    | File object storage         |
| Neo4j      | 7474 / 7687    | Graph database              |
| Prometheus | 9090           | Metrics scraping            |
| Grafana    | 3000           | Monitoring dashboard        |

```bash
docker-compose ps   # kiểm tra status
```

### 2 — Khởi động Backend

```bash
mvn spring-boot:run
```

Backend: **http://localhost:8080**

### 3 — Khởi động Frontend

```bash
cd frontend
npm install   # chỉ lần đầu
npm run dev
```

Frontend: **http://localhost:5173**

---

## Tạo test data

```bash
python3 genLog.py
```

Tạo ra:

| File               | Nội dung                                                     |
|--------------------|--------------------------------------------------------------|
| `soc_logs.json`    | ~1000 structured events (NDJSON) — upload qua File Upload    |
| `soc_freetext.txt` | 25 free-text alert templates — dán vào Free Text panel       |

**Coverage:**
- AUTH events: User + Host + IP nodes, bao gồm FQDN và email usernames để trigger SAME_AS dedup
- PROCESS events: FileHash + Process nodes, ~40% reuse known hashes để test `ON MATCH SET`
- NETWORK events: IP→IP CONNECTED_TO, IP→Domain RESOLVES_TO
- ALERT events: tất cả 10 entity types, đặc biệt có 1 `full_blast` alert với đủ hết entity fields
- Free text: brute-force, malware/LOLBIN, C2/DNS tunneling, phishing, cloud exfiltration, CVE exploit, APT chain

```bash
# Upload structured logs
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@soc_logs.json"

# Hoặc dùng UI: mở http://localhost:5173 → kéo thả soc_logs.json vào File Upload panel
# Copy từng dòng trong soc_freetext.txt → dán vào ô "Nhập Alert (Free Text)" → Gửi Alert
```

---

## API Reference

### Ingestion

```bash
# Free text (LLM parse)
POST /api/v1/ingest
Content-Type: text/plain

Phát hiện đăng nhập đáng ngờ từ IP 185.220.101.42 vào WIN-DC01 lúc 3 giờ sáng

# File upload (NDJSON — mỗi dòng 1 event)
POST /api/files/upload
Content-Type: multipart/form-data

file=@soc_logs.json
```

### Graph Query

```bash
# Danh sách entity theo loại
GET /api/graph/entities/{type}
# type: user | host | ip | domain | filehash | url | process | cloudresource | email | cve

# Neighbor nodes (1–5 hops)
GET /api/graph/{type}/{value}/neighbors?hops=2

# Attack path
GET /api/graph/path?fromType=user&fromValue=nghia&toType=ip&toValue=185.220.101.42&maxHops=4
```

### Enrichment Query (MongoDB)

```bash
# Lấy enrichment data của một entity từ audit_logs
GET /api/enrichment/entity?type=ip&value=185.220.101.42
GET /api/enrichment/entity?type=filehash&value=abc123def456...

# Response ví dụ (IP):
# {
#   "geo":    { "country": "DE", "city": "Frankfurt", "asn": "AS396356" },
#   "ipIntel":{ "abuseScore": 92, "threatLevel": "CRITICAL", "malicious": true }
# }
```

---

## Endpoints UI & Monitoring

| URL                                        | Mô tả                                      |
|--------------------------------------------|---------------------------------------------|
| http://localhost:5173                      | Frontend — danh sách entity + graph         |
| http://localhost:8080/swagger-ui.html      | Swagger UI                                  |
| http://localhost:8080/actuator/health      | Health check                                |
| http://localhost:8080/actuator/prometheus  | Prometheus metrics                          |
| http://localhost:9090                      | Prometheus UI                               |
| http://localhost:3000                      | Grafana (admin / admin)                     |
| http://localhost:7474                      | Neo4j Browser (neo4j / password123)         |
| http://localhost:9001                      | MinIO Console (admin / password123)         |

### Neo4j Quick Queries

```cypher
-- Xem toàn bộ graph (giới hạn 100 node)
MATCH (n)-[r]->(m) RETURN n, r, m LIMIT 100

-- Xem các SAME_AS dedup links
MATCH (a)-[r:SAME_AS]->(b) RETURN a, r, b

-- User tấn công từ IP nào
MATCH (u:User)-[r:ALERTED_FROM]->(ip:IP) RETURN u.username, ip.address, r.alertName

-- CVE nào ảnh hưởng host nào
MATCH (cve:Cve)-[:AFFECTS]->(h:Host) RETURN cve.cveId, h.hostname

-- Attack path ngắn nhất
MATCH path = shortestPath((u:User {username:'nghia'})-[*1..5]-(ip:IP {address:'185.220.101.42'}))
RETURN path
```

### Grafana Dashboard "SOC Entity Management"

- **Entity Saved** — số entity ghi vào Neo4j theo event type
- **Events Processed** — tổng event qua Kafka pipeline
- **Enrichment Duration** — latency p50/p99 bước làm giàu
- **HTTP Request Rate / Error Rate** — traffic vào REST API
- **JVM Memory, GC, Thread count** — sức khoẻ JVM

---

## Dừng hệ thống

```bash
docker-compose down        # dừng
docker-compose down -v     # dừng + xóa toàn bộ data
```
