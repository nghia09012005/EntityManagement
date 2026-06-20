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
    ├── EntityListPage.jsx       # Trang chủ — danh sách entity theo tab
    └── EntityDetailPage.jsx     # Chi tiết entity + graph 1-hop
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
- Bảng quan hệ 1-hop (relationship type, firstSeen, lastSeen, count)
- Graph visualization tương tác (xem bên dưới)

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

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| `POST` | `/api/files/upload` | Upload file log (multipart) |
| `GET`  | `/api/graph/entities/{type}` | Danh sách entity theo loại |
| `GET`  | `/api/graph/{label}/{value}/neighbors?hops=1` | Subgraph 1-hop quanh entity |

---

## Dependencies chính

| Package | Dùng cho |
|---------|----------|
| `react` + `react-dom` | UI framework |
| `react-router-dom` | Client-side routing |
| `vis-network` | Graph visualization |
| `vis-data` | DataSet (nodes/edges mutables) |
