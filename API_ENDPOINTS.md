# API Endpoints

Base URL: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## Authentication

Các endpoint public:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /actuator/**`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`

Các endpoint còn lại cần JWT:

```http
Authorization: Bearer <token>
```

JWT chứa `tenantId` và `role`. Backend dùng `tenantId` để tách dữ liệu giữa các user/tenant trong MongoDB và Neo4j.

---

## 1. Auth

### POST `/api/auth/register`

Tạo user mới. Mỗi user được cấp một `tenantId` riêng.

**Request**

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "username": "analyst01",
  "email": "analyst01@example.com",
  "password": "secret123"
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `username` | string | yes | 3-50 ký tự |
| `email` | string | yes | đúng định dạng email |
| `password` | string | yes | tối thiểu 6 ký tự |

**Response `200 OK`**

```json
{
  "token": "<jwt>",
  "username": "analyst01",
  "email": "analyst01@example.com",
  "role": "ANALYST",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error `400 Bad Request`**

```json
{
  "error": "Tên đăng nhập đã tồn tại trong hệ thống"
}
```

---

### POST `/api/auth/login`

Đăng nhập và lấy JWT.

**Request**

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "username": "analyst01",
  "password": "secret123"
}
```

| Field | Type | Required |
|---|---|---:|
| `username` | string | yes |
| `password` | string | yes |

**Response `200 OK`**

```json
{
  "token": "<jwt>",
  "username": "analyst01",
  "email": "analyst01@example.com",
  "role": "ANALYST",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 2. Ingestion

### POST `/api/v1/ingest`

Gửi một log/event vào Kafka topic `raw-logs`.

Endpoint này nhận cả **free-text** và **JSON string**. Backend nhận body dưới dạng raw string, bọc thêm `tenantId`, rồi đẩy vào pipeline.

**Auth:** required

**Request: free-text**

```http
POST /api/v1/ingest
Content-Type: text/plain
Authorization: Bearer <token>
```

```text
Phát hiện đăng nhập đáng ngờ từ IP 192.168.1.100 lúc 3 giờ sáng vào máy chủ SRV-DC01
```

**Request: JSON**

```http
POST /api/v1/ingest
Content-Type: text/plain
Authorization: Bearer <token>
```

```json
{
  "eventType": "ALERT",
  "alertName": "Brute Force",
  "severity": "HIGH",
  "targetIp": "192.168.1.100",
  "targetHost": "SRV-DC01",
  "targetUser": "admin"
}
```

**Response `202 Accepted`**

```text
Log queued
```

**Pipeline**

```text
/api/v1/ingest
  -> IngestionService
  -> Kafka: raw-logs
  -> ParserWorker
  -> Kafka: normalized-events
  -> GraphWorker + EnrichmentWorker
```

**Parse behavior**

- JSON có `eventType` hợp lệ có thể được deserialize trực tiếp thành `BaseEvent`.
- JSON structured sẽ được `ParserDispatcher` auto-detect qua các parser.
- Free-text sẽ đi qua LLM fallback chain để trích xuất alert fields.
- Lỗi parse/publish được đẩy sang `dead-letter-queue`.

---

## 3. File Upload

### POST `/api/files/upload`

Upload file log lên MinIO. Sau đó backend đọc từng dòng trong file và đẩy từng dòng vào Kafka topic `raw-logs`.

**Auth:** required

**Request**

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@dataset/alert-events.log"
```

| Param | Type | Required | Description |
|---|---|---:|---|
| `file` | multipart file | yes | File log, ví dụ `.log`, `.txt`, `.json` |

**Response `202 Accepted`**

```json
{
  "fileName": "uuid_alert-events.log",
  "url": "http://localhost:9000/uploads/uuid_alert-events.log"
}
```

**Note**

- Upload trả về ngay sau khi file được lưu vào MinIO.
- Việc đọc file và ingest từng dòng chạy async.
- Mỗi dòng trong file được xử lý giống `POST /api/v1/ingest`.

---

## 4. Graph

### Supported Entity Types

Các API graph nhận `type`, `label`, `fromType`, `toType` theo bảng sau:

| API value | Neo4j label | ID property | Example |
|---|---|---|---|
| `user` | `User` | `username` | `admin` |
| `host` | `Host` | `hostname` | `srv-dc01` |
| `ip` | `IP` | `address` | `192.168.1.100` |
| `domain` | `Domain` | `name` | `evil.example.com` |
| `filehash` | `FileHash` | `hash` | `d41d8cd98f00b204e9800998ecf8427e` |
| `url` | `Url` | `url` | `http://evil.example.com/a` |
| `process` | `Process` | `name` | `powershell.exe` |
| `cloudresource` | `CloudResource` | `resourceId` | `arn:aws:s3:::bucket` |
| `email` | `Email` | `address` | `admin@example.com` |
| `cve` | `Cve` | `cveId` | `CVE-2021-44228` |

---

### GET `/api/graph/entities/{type}`

Liệt kê tất cả entity của một loại trong tenant hiện tại.

**Auth:** required

| Param | Type | Required | Description |
|---|---|---:|---|
| `type` | path | yes | Một trong các supported entity types |

**Request**

```http
GET /api/graph/entities/ip
Authorization: Bearer <token>
```

**Response `200 OK`**

```json
[
  {
    "id": "IP:192.168.1.100",
    "label": "IP",
    "properties": {
      "tenantId": "550e8400-e29b-41d4-a716-446655440000",
      "address": "192.168.1.100"
    }
  }
]
```

**Error `400 Bad Request`**

Trả về khi `type` không hợp lệ.

---

### GET `/api/graph/{label}/{value}/neighbors`

Lấy graph lân cận của một entity.

**Auth:** required

| Param | Type | Required | Description |
|---|---|---:|---|
| `label` | path | yes | Một trong các supported entity types |
| `value` | path | yes | Giá trị định danh của entity |
| `hops` | query | no | Độ sâu graph, default `1`, min `1`, max `5` |

**Limit**

- `hops=1`: tối đa 200 relationship rows
- `hops=2`: tối đa 500 relationship rows
- `hops=3..5`: tối đa 1000 relationship rows

**Request**

```http
GET /api/graph/user/admin/neighbors?hops=2
Authorization: Bearer <token>
```

**Response `200 OK`**

```json
{
  "nodes": [
    {
      "id": "User:admin",
      "label": "User",
      "properties": {
        "tenantId": "550e8400-e29b-41d4-a716-446655440000",
        "username": "admin"
      }
    },
    {
      "id": "Host:srv-dc01",
      "label": "Host",
      "properties": {
        "tenantId": "550e8400-e29b-41d4-a716-446655440000",
        "hostname": "srv-dc01"
      }
    }
  ],
  "edges": [
    {
      "from": "User:admin",
      "to": "Host:srv-dc01",
      "type": "LOGGED_IN_TO",
      "properties": {
        "firstSeen": "2026-06-18T10:00:00",
        "lastSeen": "2026-06-18T12:00:00",
        "count": 5,
        "firstEventId": "uuid-a",
        "lastEventId": "uuid-b"
      }
    }
  ]
}
```

---

### GET `/api/graph/path`

Tìm path giữa hai entity.

**Auth:** required

| Param | Type | Required | Description |
|---|---|---:|---|
| `fromType` | query | yes | Loại entity nguồn |
| `fromValue` | query | yes | Giá trị entity nguồn |
| `toType` | query | yes | Loại entity đích |
| `toValue` | query | yes | Giá trị entity đích |
| `maxHops` | query | no | Default `6`, min `1`, max `10` |
| `mode` | query | no | `shortest` hoặc `all`, default `shortest` |

**Request**

```http
GET /api/graph/path?fromType=user&fromValue=admin&toType=domain&toValue=evil.com&maxHops=6&mode=all
Authorization: Bearer <token>
```

**Response `200 OK`, found**

```json
{
  "nodes": [
    {
      "id": "User:admin",
      "label": "User",
      "properties": {
        "username": "admin"
      }
    },
    {
      "id": "Domain:evil.com",
      "label": "Domain",
      "properties": {
        "name": "evil.com"
      }
    }
  ],
  "edges": [
    {
      "from": "IP:192.168.1.100",
      "to": "Domain:evil.com",
      "type": "RESOLVES_TO",
      "properties": {
        "count": 2
      }
    }
  ],
  "found": true,
  "pathCount": 1,
  "shortestLength": 3
}
```

**Response `200 OK`, not found**

```json
{
  "nodes": [],
  "edges": [],
  "found": false,
  "pathCount": 0,
  "shortestLength": 0
}
```

---

## 5. Enrichment

### GET `/api/enrichment/event/{eventId}`

Lấy enrichment data của một event từ MongoDB collection `audit_logs`.

**Auth:** required

| Param | Type | Required | Description |
|---|---|---:|---|
| `eventId` | path | yes | Event ID được lưu trong `firstEventId` hoặc `lastEventId` của relationship |

**Request**

```http
GET /api/enrichment/event/uuid-a
Authorization: Bearer <token>
```

**Response `200 OK`**

```json
{
  "geo": {
    "country": "Vietnam",
    "city": "Hanoi",
    "asn": "AS7552"
  },
  "ipIntel": {
    "abuseScore": 80,
    "isMalicious": true,
    "threatLevel": "HIGH"
  },
  "malware": {
    "verdict": "MALICIOUS",
    "family": "example-family"
  }
}
```

Nếu không có enrichment, response có thể là object rỗng:

```json
{}
```

---

## 6. Dead Letter Queue

### GET `/api/dlq/events`

Liệt kê các event xử lý lỗi đã được lưu trong MongoDB collection `dlq_events`.

**Auth:** required

| Param | Type | Required | Default | Description |
|---|---|---:|---|---|
| `page` | query | no | `0` | Page index |
| `size` | query | no | `20` | Page size |

**Request**

```http
GET /api/dlq/events?page=0&size=20
Authorization: Bearer <token>
```

**Response `200 OK`**

```json
{
  "content": [
    {
      "id": "uuid-dlq",
      "sourceTopic": "normalized-events",
      "originalPayload": "{\"eventType\":\"ALERT\"}",
      "error": "Neo4j down",
      "errorClass": "RuntimeException",
      "failedAt": "2026-06-25T12:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

---

### GET `/api/dlq/summary`

Đếm số DLQ event theo source topic.

**Auth:** required

**Request**

```http
GET /api/dlq/summary
Authorization: Bearer <token>
```

**Response `200 OK`**

```json
{
  "raw-logs": 2,
  "normalized-events": 5,
  "enriched-events": 0,
  "total": 7
}
```

---

## 7. Threat Intel

### GET `/api/internal/threat-intel/provider`

Kiểm tra provider threat intel đang active.

**Auth:** required

**Request**

```http
GET /api/internal/threat-intel/provider
Authorization: Bearer <token>
```

**Response `200 OK`**

```text
virustotal
```

Nếu không có provider:

```text
none
```

---

## 8. Actuator & Monitoring

Các endpoint actuator được public theo `SecurityConfig`.

### GET `/actuator/health`

Kiểm tra health tổng quát.

```http
GET /actuator/health
```

### GET `/actuator/health/liveness`

Liveness probe.

```http
GET /actuator/health/liveness
```

### GET `/actuator/health/readiness`

Readiness probe.

```http
GET /actuator/health/readiness
```

### GET `/actuator/info`

Thông tin app nếu được cấu hình.

```http
GET /actuator/info
```

### GET `/actuator/metrics`

Danh sách metric names.

```http
GET /actuator/metrics
```

### GET `/actuator/metrics/{metricName}`

Chi tiết một metric.

```http
GET /actuator/metrics/http.server.requests
```

### GET `/actuator/prometheus`

Prometheus scrape endpoint.

```http
GET /actuator/prometheus
```

---

## Relationship Types

Mỗi relationship chính có metadata:

- `firstSeen`
- `lastSeen`
- `count`
- `firstEventId`
- `lastEventId`

| Relationship | From | To | Source event |
|---|---|---|---|
| `LOGGED_IN_TO` | User | Host | AuthenticationEvent |
| `AUTHENTICATED_TO` | IP | Host | AuthenticationEvent |
| `EXECUTED_ON` | FileHash | Host | ProcessEvent |
| `EXECUTED_ON` | Process | Host | ProcessEvent / AlertEvent |
| `HASH_OF` | FileHash | Process | ProcessEvent |
| `CONNECTED_TO` | IP | IP | NetworkEvent |
| `RESOLVES_TO` | IP | Domain | NetworkEvent / AlertEvent |
| `ALERTED_FROM` | User | IP | AlertEvent |
| `TARGETED_AT` | IP | Host | AlertEvent |
| `DETECTED_ON` | FileHash | IP | AlertEvent |
| `ACCESSED` | IP | Url | AlertEvent |
| `ACCESSED` | User | CloudResource | AlertEvent |
| `HAS_EMAIL` | User | Email | AlertEvent |
| `AFFECTS` | Cve | Host | AlertEvent |
| `SAME_AS` | Entity | Entity | GraphDeduplicationService |

`SAME_AS` relationships are created by the scheduled deduplication job and include:

- `confidence`
- `reason`
- `detectedAt`
- `tenantId`

---

## Kafka Topics

| Topic | Producer | Consumer |
|---|---|---|
| `raw-logs` | Ingestion API / file ingestion | ParserWorker |
| `normalized-events` | ParserWorker | GraphWorker, EnrichmentWorker |
| `dead-letter-queue` | Any worker via DeadLetterPublisher | DlqWorker |

---

## Status Codes

| Code | Meaning |
|---:|---|
| `200 OK` | Request thành công |
| `202 Accepted` | Request được nhận và xử lý async |
| `400 Bad Request` | Input không hợp lệ, ví dụ entity type sai |
| `401 Unauthorized` | Thiếu hoặc sai JWT |
| `403 Forbidden` | Bị chặn bởi security policy |
| `500 Internal Server Error` | Lỗi runtime chưa được handle |
