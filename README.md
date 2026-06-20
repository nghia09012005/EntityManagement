# SOC (Security Operations Center) Platform

Hệ thống SOC chuyên dụng cho việc thu thập, phân tích, phát hiện và phản ứng với các mối đe dọa bảo mật theo thời gian thực.

Kiến trúc được thiết kế theo mô hình:

- Pipeline-based Architecture
- Event-driven Processing
- Management Plane
- Real-time Detection

---

# 📌 Features

- Real-time log ingestion
- Multi-source log parsing
- Event normalization
- Threat intelligence enrichment
- Rule-based detection engine
- Runtime rule management
- Graph-based attack analysis
- Kafka streaming pipeline
- RESTful management APIs
- JWT authentication & RBAC
- Multi-database architecture

---

# 🏗 System Architecture

```text
                        +------------------+
                        |  Log Sources     |
                        |------------------|
                        | Windows          |
                        | Linux            |
                        | Firewall         |
                        | Cloud Provider   |
                        | Okta             |
                        +--------+---------+
                                 |
                                 v
                    +----------------------+
                    |     Ingestion API    |
                    +----------------------+
                                 |
                                 v
                          +-------------+
                          |    Kafka    |
                          +-------------+
                                 |
        ------------------------------------------------
        |                      |                       |
        v                      v                       v

+---------------+    +----------------+     +----------------+
| Parser Worker | -> | Enrichment     | ->  | Detection      |
|               |    | Worker         |     | Engine         |
+---------------+    +----------------+     +----------------+
                                                    |
                                                    v
                                           +----------------+
                                           | Alert Engine   |
                                           +----------------+
                                                    |
                     ---------------------------------------------------
                     |                         |                       |
                     v                         v                       v

              +-------------+         +-------------+         +-------------+
              | PostgreSQL  |         | MongoDB    |         | Neo4j       |
              | Alerts      |         | Raw Logs   |         | Graph       |
              +-------------+         +-------------+         +-------------+

```
# Project Structure
```
src/main/java/com/example/soc/
├── SocApplication.java
│
├── ingestion/              # Data Pipeline: Tiếp nhận log
│   ├── controller/         # Endpoint nhận log (Webhook/Push)
│   ├── dto/                # Request DTO cho log thô
│   └── service/            # Logic chuyển log vào Queue
│
├── parser/                 # Data Pipeline: Parsing logs
│   ├── core/               # Base Parser & Dispatcher
│   ├── windows/            # Các parser cụ thể
│   ├── linux/
│   ├── cloudtrail/
│   ├── suricata/
│   └── okta/
│
├── normalized/             # Data Models (POJOs)
│   ├── base/               # Các class trừu tượng
│   ├── event/              # Event types (Auth, Process, etc)
│   └── alert/              # Alert types (Malware, Login, etc)
│
├── enrichment/             # Data Pipeline: Làm giàu dữ liệu
│   ├── core/
│   ├── geoip/
│   ├── threatintel/
│   ├── asset/
│   └── identity/
│
├── detection/              # Data Pipeline: Phát hiện mối đe dọa
│   ├── engine/             # Engine chạy Rules
│   ├── rules/              # Các class định nghĩa Rule
│   └── model/              # Kết quả phát hiện
│
├── management/             # MỚI: API CRUD (Quản lý hệ thống)
│   ├── controller/         # API để CRUD Rules, Assets, Users
│   ├── service/            # Business logic cho quản trị
│   └── dto/                # Request/Response DTO cho API
│
├── graph/                  # Phân tích quan hệ (Neo4j)
│   ├── entity/
│   ├── relation/
│   ├── mapper/
│   └── repository/
│
├── storage/                # Truy cập dữ liệu (DB)
│   ├── mongodb/            # Lưu Raw Logs
│   ├── postgres/           # Lưu Alerts & Cấu hình (Rules, Assets)
│   └── repository/         # Các interface Repository dùng chung
│
├── queue/                  # Message Broker (Kafka)
│   ├── kafka/
│   └── worker/             # Các worker xử lý theo luồng
│
├── security/               # MỚI: Bảo mật API (JWT, Auth)
│   ├── config/
│   ├── service/
│   └── filter/
│
└── common/                 # Tiện ích dùng chung
    ├── exception/
    ├── util/
```

# 🛡️ SIEM Platform — Architecture Documentation

## ⚙️ Core Modules

### 1. Ingestion Module

Chịu trách nhiệm tiếp nhận log từ nhiều nguồn khác nhau.

#### Responsibilities

- REST API ingestion
- Syslog receiver
- Webhook ingestion
- Validate raw payload
- Push event vào Kafka

#### Example Flow
```
Client -> /api/v1/logs -> Kafka Topic
```
---

### 2. Parser Module

Chuẩn hóa dữ liệu log thành unified schema.

#### Supported Parsers

- Windows Event Parser
- Linux Syslog Parser
- Okta Parser
- Firewall Parser
- CloudTrail Parser

#### Example

**Raw Log:**

```json
{
  "event_id": 4625,
  "user": "admin",
  "ip": "1.1.1.1"
}
```

**Normalized Event:**

```json
{
  "eventType": "AUTH_FAILURE",
  "username": "admin",
  "sourceIp": "1.1.1.1",
  "provider": "WINDOWS"
}
```

---

### 3. Enrichment Module

Bổ sung metadata cho event.

#### Enrichment Workers

- GeoIP Worker
- Threat Intel Worker
- Asset Context Worker
- User Context Worker
- DNS Enrichment Worker

#### Example

```json
{
  "sourceIp": "8.8.8.8",
  "country": "US",
  "isMalicious": false,
  "assetOwner": "SOC Team"
}
```

---

### 4. Detection Engine

Phát hiện tấn công dựa trên rules.

#### Detection Types

- Brute Force Detection
- Impossible Travel
- IOC Match
- Privilege Escalation
- Lateral Movement
- Suspicious PowerShell
- Multiple Failed Login

#### Example Rule

```yaml
name: brute-force-detection
condition:
  failed_login > 5
window: 5m
severity: HIGH
```

---

## 🔥 Runtime Rule Management

Rules có thể cập nhật runtime mà không cần restart service.

#### Flow
```
Admin API
↓
PostgreSQL
↓
Rule Cache Reload
↓
Detection Engine
```
#### Features

- Enable/Disable rule
- Dynamic reload
- Rule versioning
- Rule validation
- Rule testing

---

## 🌐 Graph Analytics (Neo4j)

Module graph hỗ trợ:

- Multi-hop investigation
- Entity relationship analysis
- Attack path finding
- IOC correlation
- Lateral movement detection

#### Example Graph

```cypher
(User)-[:LOGIN_FROM]->(IP)
(IP)-[:RESOLVES_TO]->(DOMAIN)
(User)-[:ACCESS]->(HOST)
```

---

## 🗄️ Storage Architecture

### MongoDB

Lưu:
- Raw logs
- Normalized events
- Large unstructured data

### PostgreSQL

Lưu:
- Alerts
- Rules
- Asset inventory
- User configuration
- Management data

### Neo4j

Lưu:
- Entity relationship
- Attack graph
- IOC correlation graph

---

## 🧵 Kafka Streaming

Kafka được dùng để:

- Decouple services
- Buffer ingestion spikes
- Async processing
- Retry handling
- Event replay

#### Suggested Topics
```
raw-logs
parsed-events
enriched-events
alerts
dead-letter-topic
```


---

## 🔐 Security

#### Authentication

- JWT Authentication
- Refresh Token
- Role-based Access Control

#### Roles

| Role     | Description          |
|----------|----------------------|
| ADMIN    | Full access          |
| ANALYST  | Detection & alerts   |
| VIEWER   | Read-only access     |

---

## 🚀 API Examples

> Xem đầy đủ tại [API_ENDPOINTS.md](API_ENDPOINTS.md) hoặc [Swagger UI](http://localhost:8080/swagger-ui.html)

### Ingest log (tự detect loại event)

```bash
POST /api/v1/ingest
Content-Type: text/plain

{"user":"admin","ip":"1.2.3.4","is_success":true,"workstation":"WIN-PC01"}
```

### Upload file log

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@dataset/alert-events.log"
```

### Truy vấn graph entity

```bash
GET /api/graph/user/admin/neighbors?hops=2
GET /api/graph/entities/ip
```

---

## 🚀 Cách chạy dự án

### Yêu cầu

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Docker & Docker Compose | latest |

---

### Bước 1 — Khởi động Infrastructure

```bash
docker-compose up -d
```

Chờ tất cả service healthy (~30 giây):

| Service | Port | Mục đích |
|---|---|---|
| Kafka | 9092 | Message queue |
| Zookeeper | 2181 | Kafka coordinator |
| MongoDB | 27017 | Lưu raw log & audit |
| Redis | 6379 | Cache GeoIP / malware |
| MinIO | 9000 / 9001 | Object storage cho file log |
| Neo4j | 7474 / 7687 | Graph database |
| Prometheus | 9090 | Metrics scraping |
| Grafana | 3000 | Dashboard monitoring |

Kiểm tra status:
```bash
docker-compose ps
```

---

### Bước 2 — Khởi động Backend (Spring Boot)

```bash
mvn spring-boot:run
```

Backend chạy tại: **http://localhost:8080**

---

### Bước 3 — Khởi động Frontend (React)

```bash
cd frontend
npm install       # chỉ cần lần đầu
npm run dev
```

Frontend chạy tại: **http://localhost:5173**

---

### Bước 4 — Upload sample data

Upload 4 file log mẫu để tạo đầy đủ entity và quan hệ trong Neo4j:

```bash
# Window login events (User, Host, IP)
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@dataset/window-login.log"

# Process events (FileHash, Host)
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@dataset/process-sample.log"

# Network traffic (IP, Domain)
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@dataset/network-traffic.log"

# Alert events (tất cả 5 entity)
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@dataset/alert-events.log"
```

---

### Bước 5 — Xem kết quả

| URL | Mô tả |
|---|---|
| http://localhost:5173 | UI danh sách & graph entity |
| http://localhost:8080/swagger-ui.html | Swagger UI — API documentation |
| http://localhost:8080/v3/api-docs | OpenAPI JSON spec |
| http://localhost:8080/actuator/health | Health check tổng thể |
| http://localhost:8080/actuator/health/liveness | Liveness probe |
| http://localhost:8080/actuator/health/readiness | Readiness probe |
| http://localhost:8080/actuator/prometheus | Prometheus metrics endpoint |
| http://localhost:9090 | Prometheus UI |
| http://localhost:3000 | Grafana dashboard (admin / admin) |
| http://localhost:7474 | Neo4j Browser (neo4j / password123) |
| http://localhost:9001 | MinIO Console (admin / password123) |

Query nhanh trong Neo4j Browser:
```cypher
MATCH (n)-[r]->(m) RETURN n, r, m LIMIT 50
```

#### Grafana dashboard "SOC Entity Management" hiển thị:
- **Entity Saved** — số entity được ghi vào Neo4j theo loại (Auth/Process/Network/Alert)
- **Events Processed** — tổng events qua Kafka pipeline
- **Enrichment Duration** — latency p50/p99 của bước làm giàu dữ liệu
- **HTTP Request Rate / Error Rate** — traffic vào REST API
- **JVM Memory, GC, Thread count** — sức khoẻ JVM

---

### Dừng toàn bộ

```bash
# Dừng Docker services
docker-compose down

# Xóa luôn data (nếu cần reset)
docker-compose down -v
```

---

## 🐳 Running with Docker

**Start dependencies:**

```bash
docker-compose up -d
```

**Services:**

| Service     | Description              |
|-------------|--------------------------|
| Kafka       | Event streaming          |
| Zookeeper   | Kafka coordination       |
| MongoDB     | Raw & normalized storage |
| Redis       | Enrichment cache         |
| MinIO       | File object storage      |
| Neo4j       | Graph analytics          |
| Prometheus  | Metrics scraping         |
| Grafana     | Monitoring dashboard     |
















