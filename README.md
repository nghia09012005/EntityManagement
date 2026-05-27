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

### Ingest Log

```http
POST /api/v1/logs
```

**Request:**

```json
{
  "source": "windows",
  "payload": {
    "event_id": 4625
  }
}
```

---

### Create Rule

```http
POST /api/v1/rules
```

**Request:**

```json
{
  "name": "Brute Force",
  "enabled": true,
  "severity": "HIGH"
}
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
| PostgreSQL  | Structured management DB |
| Neo4j       | Graph analytics          |
















