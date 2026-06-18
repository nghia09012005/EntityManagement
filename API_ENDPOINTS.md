# API Endpoints

Base URL: `http://localhost:8080`

---

## 1. Ingestion — Nhận log trực tiếp

### POST `/api/v1/ingest/{source}`

Gửi một dòng log thô vào Kafka queue để xử lý.

| Param | Loại | Bắt buộc | Mô tả |
|---|---|---|---|
| `source` | path | ✅ | Nguồn log: `windows` hoặc `process` |
| body | raw string | ✅ | Nội dung log thô (JSON hoặc plain text) |

**Request:**
```
POST /api/v1/ingest/windows
Content-Type: text/plain

{"eventType":"AUTHENTICATION","username":"admin","ipAddress":"1.2.3.4","workstation":"WIN-PC01","success":true}
```

**Response:** `202 Accepted`
```
Log queued
```

---

## 2. File Upload — Upload file log

### POST `/api/files/upload`

Upload file log lên MinIO, sau đó tự động đọc từng dòng và đẩy vào Kafka (async).  
Source được suy ra từ tên file: chứa `windows`/`auth`/`login` → `windows`; chứa `process`/`proc` → `process`.

| Param | Loại | Bắt buộc | Mô tả |
|---|---|---|---|
| `file` | multipart form-data | ✅ | File log (.log, .txt, …) tối đa 100MB |

**Request:**
```
POST /api/files/upload
Content-Type: multipart/form-data

file=@windows-auth.log
```

**Response:** `202 Accepted`
```json
{
  "fileName": "windows-auth.log",
  "url": "http://localhost:9000/uploads/windows-auth.log"
}
```

---

## 3. Graph — Truy vấn quan hệ entity

### GET `/api/graph/{label}/{value}/neighbors`

Lấy các node và quan hệ lân cận của một entity (1 hoặc 2 hop).

| Param | Loại | Bắt buộc | Giá trị hợp lệ | Mô tả |
|---|---|---|---|---|
| `label` | path | ✅ | `user` \| `host` \| `ip` \| `domain` \| `filehash` | Loại entity |
| `value` | path | ✅ | e.g. `admin`, `192.168.1.1` | Giá trị định danh entity |
| `hops` | query | ❌ | `1` \| `2` (default: `1`) | Độ sâu truy vấn |

| label | Trường định danh | Ví dụ value |
|---|---|---|
| `user` | `username` | `admin` |
| `host` | `hostname` | `WIN-PC01` |
| `ip` | `address` | `192.168.1.100` |
| `domain` | `name` | `malware.example.com` |
| `filehash` | `hash` | `d41d8cd98f00b204e9800998ecf8427e` |

**Request:**
```
GET /api/graph/user/admin/neighbors?hops=2
```

**Response:** `200 OK`
```json
{
  "nodes": [
    { "id": "User:admin",    "label": "User",    "properties": { "username": "admin" } },
    { "id": "Host:WIN-PC01", "label": "Host",    "properties": { "hostname": "WIN-PC01" } },
    { "id": "IP:1.2.3.4",   "label": "IP",      "properties": { "address": "1.2.3.4", "country": "Vietnam", "asn": "AS7552 - Viettel" } }
  ],
  "edges": [
    { "from": "User:admin", "to": "Host:WIN-PC01", "type": "LOGGED_IN_TO",   "properties": { "firstSeen": "2026-06-18T10:00:00", "lastSeen": "2026-06-18T12:00:00", "count": 5 } },
    { "from": "IP:1.2.3.4", "to": "Host:WIN-PC01", "type": "AUTHENTICATED_TO", "properties": { "firstSeen": "2026-06-18T10:00:00", "lastSeen": "2026-06-18T10:00:00", "count": 1 } }
  ]
}
```

---

## 4. Threat Intel — Thông tin nhà cung cấp

### GET `/api/internal/threat-intel/provider`

Kiểm tra nhà cung cấp threat intel đang active.

**Request:**
```
GET /api/internal/threat-intel/provider
```

**Response:** `200 OK`
```
virustotal
```
hoặc `none` nếu không có provider nào được cấu hình.

---

## Relationship Types (Neo4j)

| Quan hệ | Từ | Đến | Nguồn event | Properties |
|---|---|---|---|---|
| `LOGGED_IN_TO` | User | Host | AuthenticationEvent | firstSeen, lastSeen, count |
| `AUTHENTICATED_TO` | IP | Host | AuthenticationEvent | firstSeen, lastSeen, count |
| `EXECUTED_ON` | FileHash | Host | ProcessEvent | firstSeen, lastSeen, count, processName |
| `CONNECTED_TO` | IP | IP | NetworkEvent | firstSeen, lastSeen, count, dstPort |
| `RESOLVES_TO` | IP | Domain | NetworkEvent | firstSeen, lastSeen, count |
| `ALERTED_FROM` | User | IP | AlertEvent | firstSeen, lastSeen, count, alertName, severity |
| `DETECTED_ON` | FileHash | IP | AlertEvent | firstSeen, lastSeen, count |
