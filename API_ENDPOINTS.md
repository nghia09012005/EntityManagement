# API Endpoints

Base URL: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## 1. Ingestion — Nhận log trực tiếp

### POST `/api/v1/ingest`

Gửi một dòng log thô vào Kafka queue. Hệ thống **tự động nhận dạng** loại event từ nội dung JSON, không cần truyền source.

| Param | Loại | Bắt buộc | Mô tả |
|---|---|---|---|
| body | raw string / JSON | ✅ | Nội dung log thô |

**Auto-detect logic:**
- Có field `eventType` → Jackson polymorphism (`AUTHENTICATION`, `PROCESS`, `NETWORK`, `ALERT`)
- Có `processName` / `commandLine` → ProcessEvent
- Có `srcIp` / `dstIp` → NetworkEvent
- Có `alertName` / `severity` → AlertEvent
- Còn lại → AuthenticationEvent (Windows)

**Request:**
```
POST /api/v1/ingest
Content-Type: text/plain

{"user":"admin","ip":"1.2.3.4","is_success":true,"workstation":"WIN-PC01"}
```

```
POST /api/v1/ingest
Content-Type: text/plain

{"eventType":"ALERT","alertName":"Brute Force","severity":"HIGH","targetIp":"1.2.3.4"}
```

**Response:** `202 Accepted`
```
Log queued
```

---

## 2. File Upload — Upload file log

### POST `/api/files/upload`

Upload file log lên MinIO, sau đó tự động đọc từng dòng và đẩy vào Kafka (async).
Mỗi dòng được **tự động nhận dạng** loại event — không phụ thuộc vào tên file.

| Param | Loại | Bắt buộc | Mô tả |
|---|---|---|---|
| `file` | multipart form-data | ✅ | File log (.log, .txt, …) |

**Request:**
```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@dataset/alert-events.log"
```

**Response:** `202 Accepted`
```json
{
  "fileName": "alert-events.log",
  "url": "http://localhost:9000/uploads/alert-events.log"
}
```

---

## 3. Graph — Truy vấn quan hệ entity

### GET `/api/graph/{label}/{value}/neighbors`

Lấy các node và quan hệ lân cận của một entity (multi-hop).

| Param | Loại | Bắt buộc | Giá trị hợp lệ | Mô tả |
|---|---|---|---|---|
| `label` | path | ✅ | `user` \| `host` \| `ip` \| `domain` \| `filehash` | Loại entity |
| `value` | path | ✅ | e.g. `admin`, `192.168.1.1` | Giá trị định danh |
| `hops` | query | ❌ | `1`–`5` (default: `1`) | Độ sâu truy vấn |

> **Giới hạn row trả về:** 1-hop = 200, 2-hop = 500, 3-5 hop = 1 000 (tránh Cartesian explosion trên graph lớn).

| label | Trường định danh | Ví dụ |
|---|---|---|
| `user` | `username` | `admin` |
| `host` | `hostname` | `WIN-PC01` |
| `ip` | `address` | `192.168.1.100` |
| `domain` | `name` | `malware.example.com` |
| `filehash` | `hash` | `d41d8cd98f00b204e9800998ecf8427e` |

**Request:**
```
GET /api/graph/user/admin/neighbors?hops=3
```

**Response:** `200 OK`
```json
{
  "nodes": [
    { "id": "User:admin",    "label": "User", "properties": { "username": "admin" } },
    { "id": "Host:WIN-PC01", "label": "Host", "properties": { "hostname": "WIN-PC01" } },
    { "id": "IP:1.2.3.4",   "label": "IP",   "properties": { "address": "1.2.3.4", "country": "Vietnam", "asn": "AS7552" } }
  ],
  "edges": [
    { "from": "User:admin", "to": "Host:WIN-PC01", "type": "LOGGED_IN_TO",      "properties": { "firstSeen": "2026-06-18T10:00:00", "lastSeen": "2026-06-18T12:00:00", "count": 5, "firstEventId": "uuid-a", "lastEventId": "uuid-b" } },
    { "from": "IP:1.2.3.4", "to": "Host:WIN-PC01", "type": "AUTHENTICATED_TO", "properties": { "firstSeen": "2026-06-18T10:00:00", "lastSeen": "2026-06-18T10:00:00", "count": 1, "firstEventId": "uuid-c", "lastEventId": "uuid-c" } }
  ]
}
```

---

### GET `/api/graph/path`

Tìm đường đi ngắn nhất (hoặc tất cả đường ngắn nhất) giữa 2 entity bất kỳ trong graph.

| Param | Loại | Bắt buộc | Giá trị hợp lệ | Mô tả |
|---|---|---|---|---|
| `fromType` | query | ✅ | `user` \| `host` \| `ip` \| `domain` \| `filehash` | Loại entity nguồn |
| `fromValue` | query | ✅ | string | Giá trị entity nguồn |
| `toType` | query | ✅ | `user` \| `host` \| `ip` \| `domain` \| `filehash` | Loại entity đích |
| `toValue` | query | ✅ | string | Giá trị entity đích |
| `maxHops` | query | ❌ | `1`–`10` (default: `6`) | Số hop tối đa cho phép |
| `mode` | query | ❌ | `shortest` \| `all` (default: `shortest`) | `shortest` = 1 path ngắn nhất; `all` = tất cả path cùng độ dài ngắn nhất |

**Request:**
```
GET /api/graph/path?fromType=user&fromValue=admin&toType=domain&toValue=evil.com&maxHops=6&mode=all
```

**Response `found = true`:** `200 OK`
```json
{
  "nodes": [
    { "id": "User:admin",       "label": "User",   "properties": { "username": "admin" } },
    { "id": "Host:WIN-PC01",    "label": "Host",   "properties": { "hostname": "WIN-PC01" } },
    { "id": "IP:1.2.3.4",      "label": "IP",     "properties": { "address": "1.2.3.4" } },
    { "id": "Domain:evil.com",  "label": "Domain", "properties": { "name": "evil.com" } }
  ],
  "edges": [
    { "from": "User:admin",    "to": "Host:WIN-PC01",   "type": "LOGGED_IN_TO",   "properties": { "count": 5 } },
    { "from": "IP:1.2.3.4",   "to": "Host:WIN-PC01",   "type": "AUTHENTICATED_TO","properties": { "count": 1 } },
    { "from": "IP:1.2.3.4",   "to": "Domain:evil.com", "type": "RESOLVES_TO",    "properties": { "count": 2 } }
  ],
  "found": true,
  "pathCount": 2,
  "shortestLength": 3
}
```

**Response `found = false`:** `200 OK`
```json
{ "nodes": [], "edges": [], "found": false, "pathCount": 0, "shortestLength": 0 }
```

---

### GET `/api/graph/entities/{type}`

Liệt kê tất cả entity của một loại.

| Param | Loại | Bắt buộc | Giá trị hợp lệ |
|---|---|---|---|
| `type` | path | ✅ | `user` \| `host` \| `ip` \| `domain` \| `filehash` |

**Request:**
```
GET /api/graph/entities/ip
```

**Response:** `200 OK`
```json
[
  { "id": "IP:203.0.113.10",  "label": "IP", "properties": { "address": "203.0.113.10",  "country": "Vietnam",       "asn": "AS7552" } },
  { "id": "IP:198.51.100.25", "label": "IP", "properties": { "address": "198.51.100.25", "country": "United States", "asn": "AS15169" } }
]
```

---

## 4. Threat Intel — Thông tin nhà cung cấp

### GET `/api/internal/threat-intel/provider`

Kiểm tra nhà cung cấp threat intel đang active.

**Response:** `200 OK`
```
virustotal
```
hoặc `none` nếu không có provider nào được cấu hình.

---

## Relationship Types (Neo4j)

Mỗi relationship có thêm `firstEventId` và `lastEventId` — UUID trỏ tới document tương ứng trong MongoDB collection `audit_logs` (traceability Neo4j → MongoDB).

| Quan hệ | Từ | Đến | Nguồn event | Properties |
|---|---|---|---|---|
| `LOGGED_IN_TO` | User | Host | AuthenticationEvent | firstSeen, lastSeen, count, firstEventId, lastEventId |
| `AUTHENTICATED_TO` | IP | Host | AuthenticationEvent | firstSeen, lastSeen, count, firstEventId, lastEventId |
| `EXECUTED_ON` | FileHash | Host | ProcessEvent | firstSeen, lastSeen, count, processName, firstEventId, lastEventId |
| `CONNECTED_TO` | IP | IP | NetworkEvent | firstSeen, lastSeen, count, dstPort, firstEventId, lastEventId |
| `RESOLVES_TO` | IP | Domain | NetworkEvent | firstSeen, lastSeen, count, firstEventId, lastEventId |
| `ALERTED_FROM` | User | IP | AlertEvent | firstSeen, lastSeen, count, alertName, severity, firstEventId, lastEventId |
| `DETECTED_ON` | FileHash | IP | AlertEvent | firstSeen, lastSeen, count, firstEventId, lastEventId |
| `TARGETED_AT` | IP | Host | AlertEvent | firstSeen, lastSeen, count, firstEventId, lastEventId |
