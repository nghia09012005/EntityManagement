# SOC Entity Graph — Frontend

React + Vite SPA để khám phá entity graph từ hệ thống SOC (Security Operations Center).

---

## Yêu cầu

| Tool | Phiên bản |
|------|-----------|
| Node.js | ≥ 18 |
| npm | ≥ 9 |
| Backend | chạy tại `http://localhost:8080` |

---

## Cài đặt & chạy

```bash
cd frontend
npm install
npm run dev        # dev server tại http://localhost:5173
npm run build      # build production vào dist/
npm run preview    # preview production build
```

---

## Cấu trúc

```
src/
├── api.js                       # Tất cả HTTP call về backend
├── App.jsx                      # Router root + Navbar
├── components/
│   ├── EntityBadge.jsx          # Chip màu theo loại entity
│   ├── GraphView.jsx            # Visualization chính (vis-network)
│   └── UploadPanel.jsx          # Upload file log
└── pages/
    ├── EntityListPage.jsx       # / — danh sách entity theo tab
    ├── EntityDetailPage.jsx     # /entity/:type/:value — chi tiết + graph multi-hop
    └── PathFinderPage.jsx       # /paths — tìm đường đi giữa 2 entity
```

---

## Tính năng

### Upload log
- Kéo thả hoặc click để chọn file `.log` / `.txt` / `.json`
- Nhiều file cùng lúc; file được gửi tới `POST /api/files/upload` → backend đưa vào Kafka pipeline

### Entity List (`/`)
Tabs: **User · Host · IP · Domain · FileHash**

Mỗi tab gọi `GET /api/graph/entities/{type}` và hiển thị bảng entity đã tồn tại trong Neo4j.

### Entity Detail (`/entity/:type/:value`)
- Thông tin cơ bản + enrichment data (GeoIP, malware verdict, IP intel)
- Bảng quan hệ với first/last seen, count
- Bộ chọn hop: **1 / 2 / 3 / 5-hop** — load lại graph khi đổi
- Graph visualization tương tác (xem bên dưới)

### Path Finder (`/paths`)
- Chọn entity nguồn (type + value) và entity đích
- Tuỳ chọn **Max hops** (2 / 3 / 4 / 6 / 8) và **Mode** (Shortest / All shortest)
- Kết quả hiển thị trong GraphView kèm thống kê: số path, độ dài ngắn nhất, số node/edge

---

## Graph Visualization

Component `GraphView` dùng **vis-network** với các tính năng:

| Tính năng | Cách dùng |
|-----------|-----------|
| **Expand node** | Double-click vào node → tải neighbors và merge vào graph. Node đã expand có border dashed |
| **Pivot** | Right-click vào node → chuyển tới trang detail của node đó |
| **Open selected** | Click chọn node → nút "Open [label]" xuất hiện trên toolbar |
| **Fit view** | Nút **Fit** — reset zoom/pan vừa màn hình |
| **Layout Force** | Force-directed (forceAtlas2Based physics) |
| **Layout Hierarchical** | Cây top-down, tắt physics |
| **Lọc relation type** | Checkboxes — ẩn/hiện edge theo loại quan hệ |
| **Lọc thời gian** | Date range lọc edge theo `firstSeen` |
| **Export PNG** | Nút **↓ PNG** — download ảnh canvas |
| **Export JSON** | Nút **↓ JSON** — download `subgraph.json` với nodes + edges hiện tại |

---

## API endpoints dùng trong frontend

### File

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| `POST` | `/api/files/upload` | Upload file log (multipart/form-data, field `file`) |

### Graph — Entity

| Method | Endpoint | Params | Mô tả |
|--------|----------|--------|-------|
| `GET` | `/api/graph/entities/{type}` | `type`: user \| host \| ip \| domain \| filehash | Liệt kê tất cả entity theo loại |
| `GET` | `/api/graph/{label}/{value}/neighbors` | `hops` 1–5 (default 1) | Subgraph N-hop quanh entity. Limit: 1-hop=200, 2-hop=500, 3-5 hop=1000 rows |

### Graph — Path Finding

| Method | Endpoint | Params | Mô tả |
|--------|----------|--------|-------|
| `GET` | `/api/graph/path` | xem bên dưới | Tìm đường đi giữa 2 entity |

**Params của `/api/graph/path`:**

| Param | Bắt buộc | Giá trị | Mô tả |
|-------|----------|---------|-------|
| `fromType` | ✓ | user \| host \| ip \| domain \| filehash | Loại entity nguồn |
| `fromValue` | ✓ | string | Giá trị entity nguồn (vd: `admin`, `192.168.1.1`) |
| `toType` | ✓ | user \| host \| ip \| domain \| filehash | Loại entity đích |
| `toValue` | ✓ | string | Giá trị entity đích |
| `maxHops` | | 1–10 (default 6) | Số hop tối đa cho phép |
| `mode` | | `shortest` \| `all` (default `shortest`) | `shortest` = 1 path ngắn nhất; `all` = tất cả path có cùng độ dài ngắn nhất |

**Response (`PathResponse`):**

```json
{
  "nodes": [ { "id": "User:admin", "label": "User", "properties": { "username": "admin" } } ],
  "edges": [ { "from": "User:admin", "to": "Host:DC01", "type": "LOGGED_IN_TO", "properties": {} } ],
  "found": true,
  "pathCount": 2,
  "shortestLength": 3
}
```

Trả `found: false` khi không có đường đi trong `maxHops` hop.

---

## Dependencies chính

| Package | Dùng cho |
|---------|----------|
| `react` + `react-dom` | UI framework |
| `react-router-dom` | Client-side routing |
| `vis-network` | Graph visualization |
| `vis-data` | DataSet (nodes/edges mutables) |
