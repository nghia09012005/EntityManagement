import { useState, useEffect } from 'react'
import { fetchUnknownFieldStats, fetchUnknownFieldAnalytics, fetchUnknownFieldEvents } from '../api'
import { Link } from 'react-router-dom'
import { LineChartCard, PieChartCard, TimeRangeToggle } from '../components/StatsCharts'

const EVENT_TYPE_COLOR = {
  AUTHENTICATION: { bg: '#1a2d3a', color: '#63b3ed' },
  PROCESS:        { bg: '#1a3a2d', color: '#68d391' },
  NETWORK:        { bg: '#2d1a3a', color: '#b794f4' },
  THREAT:         { bg: '#3a1a1a', color: '#fc8181' },
}

function EventTypeBadge({ type }) {
  const style = EVENT_TYPE_COLOR[type] || { bg: '#2d2d2d', color: '#a0aec0' }
  return (
    <span style={{
      background: style.bg, color: style.color,
      padding: '3px 10px', borderRadius: 999, fontSize: 12,
      fontWeight: 600, letterSpacing: '0.3px',
    }}>
      {type}
    </span>
  )
}

function PayloadModal({ payload, onClose }) {
  let formatted = payload
  try {
    formatted = JSON.stringify(JSON.parse(payload), null, 2)
  } catch (_) {}
  return (
    <div onClick={onClose} style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 900,
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        width: '90vw', maxWidth: 900, maxHeight: '80vh',
        background: '#0f1521', border: '1px solid #2d3748', borderRadius: 14,
        overflow: 'hidden', display: 'flex', flexDirection: 'column',
      }}>
        <div style={{
          padding: '14px 18px', borderBottom: '1px solid #1e2432',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <span style={{ fontWeight: 700, color: '#e2e8f0' }}>Raw Payload</span>
          <button onClick={onClose} className="gv-btn" style={{ minWidth: 0, padding: '6px 10px' }}>✕</button>
        </div>
        <pre style={{
          margin: 0, padding: 18, overflow: 'auto', fontSize: 13,
          lineHeight: 1.5, fontFamily: 'monospace', color: '#cbd5e0',
        }}>{formatted || '(empty)'}</pre>
      </div>
    </div>
  )
}

export default function UnknownFieldsPage() {
  const [dashboard, setDashboard] = useState([])
  const [analytics, setAnalytics] = useState({ line: [], pie: [] })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [chartRange, setChartRange] = useState('24h')
  const [filterText, setFilterText] = useState('')
  const [unknownEvents, setUnknownEvents] = useState([])
  const [eventPage, setEventPage] = useState(0)
  const [eventTotalPages, setEventTotalPages] = useState(0)
  const [eventLoading, setEventLoading] = useState(false)
  const [selectedPayload, setSelectedPayload] = useState(null)

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        setLoading(true)
        setError(null)
        const [stats, charts] = await Promise.all([fetchUnknownFieldStats(5, chartRange), fetchUnknownFieldAnalytics(chartRange)])
        if (!active) return
        setDashboard(stats)
        setAnalytics({ line: charts.line || [], pie: charts.pie || [] })
      } catch (e) {
        if (!active) return
        setError(e.message)
      } finally {
        if (active) setLoading(false)
      }
    }
    load()
    return () => { active = false }
  }, [chartRange])

  useEffect(() => {
    let active = true
    const loadEvents = async () => {
      try {
        setEventLoading(true)
        const data = await fetchUnknownFieldEvents(eventPage, 10, chartRange, filterText)
        if (!active) return
        setUnknownEvents(data.content || [])
        setEventTotalPages(data.totalPages || 0)
      } catch (e) {
        if (!active) return
        setError(e.message)
        setUnknownEvents([])
        setEventTotalPages(0)
      } finally {
        if (active) setEventLoading(false)
      }
    }
    loadEvents()
    return () => { active = false }
  }, [chartRange, filterText, eventPage])

  useEffect(() => {
    setEventPage(0)
  }, [chartRange, filterText])

  const filteredDashboard = dashboard.map(group => ({
    ...group,
    topFields: group.topFields.filter(f =>
      !filterText || f.fieldName.toLowerCase().includes(filterText.toLowerCase())
    ),
  })).filter(group => group.topFields.length > 0)

  const totalFields = dashboard.reduce((sum, g) => sum + g.topFields.length, 0)
  const totalOccurrences = dashboard.reduce(
    (sum, g) => sum + g.topFields.reduce((s, f) => s + f.count, 0), 0
  )

  return (
    <div className="unknown-shell">
      <section className="entity-hero">
        <div>
          <div className="settings-kicker">Unknown Fields</div>
          <div className="page-title">Trường lạ & phân tích schema drift</div>
          <div className="page-sub">Theo dõi những trường không nằm trong schema chuẩn và chuẩn bị mapping cho graph.</div>
        </div>
        <Link to="/settings/field-mappings" className="gv-btn settings-link-btn" style={{ textDecoration: 'none' }}>
          ⚙ Cấu hình mapping →
        </Link>
      </section>

      <div className="chart-toolbar">
        <div>
          <div className="chart-toolbar-label">Khoảng thời gian</div>
          <div className="chart-toolbar-hint">Dashboard và chart cùng theo cùng một range</div>
        </div>
        <TimeRangeToggle value={chartRange} onChange={setChartRange} />
      </div>

      <section className="entity-search-card unknown-filter-card">
        <div className="entity-filter-bar">
          <span className="incident-section-label">🔍 Lọc theo tên trường:</span>
          <input
            className="pf-input"
            value={filterText}
            onChange={e => setFilterText(e.target.value)}
            placeholder="Nhập tên trường cần tìm..."
          />
          {filterText && (
            <button className="gv-btn" onClick={() => setFilterText('')} style={{ color: '#fc8181' }}>✕ Xoá</button>
          )}
          <span className="unknown-count-pill">
            {filterText ? `Hiển thị ${filteredDashboard.reduce((s, g) => s + g.topFields.length, 0)} / ${totalFields} trường` : ''}
          </span>
        </div>
      </section>

      <div className="incident-stats-grid unknown-stats-grid">
        {[
          { label: 'Event Types', value: dashboard.length, color: '#63b3ed' },
          { label: 'Trường lạ (top 5/type)', value: totalFields, color: '#b794f4' },
          { label: 'Tổng lần xuất hiện', value: totalOccurrences, color: '#fc8181' },
        ].map(s => (
          <div key={s.label} className="incident-summary-card">
            <div className="incident-insight-card__title">{s.label}</div>
            <div className="incident-summary-card__value" style={{ color: s.value > 0 ? s.color : '#a0aec0' }}>{s.value}</div>
          </div>
        ))}
      </div>

      <div className="grid-2" style={{ marginBottom: 20, alignItems: 'stretch' }}>
        <LineChartCard
          title="Unknown fields over time"
          subtitle={`Dữ liệu ${chartRange}`}
          data={analytics.line}
          timeRange={chartRange}
          loading={loading}
        />
        <PieChartCard
          title="Unknown fields by eventType"
          subtitle={`Tỷ lệ theo eventType · ${chartRange}`}
          data={analytics.pie}
          loading={loading}
        />
      </div>

      {eventLoading && <div className="loading">Đang tải event unknown…</div>}
      {!loading && !error && dashboard.length > 0 && filteredDashboard.length === 0 && (
        <section className="settings-card">
          <div className="empty">Không tìm thấy trường nào khớp với "<strong>{filterText}</strong>".</div>
        </section>
      )}

      <section className="settings-card" style={{ marginBottom: 16 }}>
        <div className="card-header">
          <span className="card-title">Event chứa trường lạ</span>
          <span className="card-count">{eventTotalPages > 0 ? `${eventTotalPages} trang` : ''}</span>
        </div>

        {eventLoading ? (
          <div className="loading">Đang tải event unknown…</div>
        ) : unknownEvents.length === 0 ? (
          <div className="empty">Không tìm thấy event nào chứa trường lạ trong range này.</div>
        ) : (
          <div className="entity-table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Event Type</th>
                  <th>Event ID</th>
                  <th>Field</th>
                  <th>Sample Value</th>
                  <th>Occurred At</th>
                  <th>Raw Payload</th>
                </tr>
              </thead>
              <tbody>
                {unknownEvents.map((event, idx) => (
                  <tr key={`${event.eventId}-${idx}`}>
                    <td style={{ color: '#718096' }}>{eventPage * 10 + idx + 1}</td>
                    <td><EventTypeBadge type={event.eventType} /></td>
                    <td className="incident-table-cve" style={{ whiteSpace: 'nowrap' }}>{event.eventId || '—'}</td>
                    <td><span className="mono" style={{ color: '#f6ad55', fontWeight: 600 }}>{event.fieldName}</span></td>
                    <td className="unknown-sample-cell">{event.sampleValue || '—'}</td>
                    <td className="incident-table-time">{event.occurredAt ? new Date(event.occurredAt).toLocaleString('vi-VN', { hour12: false }) : '—'}</td>
                    <td>
                      <button className="gv-btn" onClick={() => setSelectedPayload(event.rawPayload || '')}>
                        Xem
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {eventTotalPages > 1 && (
          <div className="settings-table-actions" style={{ justifyContent: 'space-between', marginTop: 12 }}>
            <button className="gv-btn" disabled={eventPage <= 0} onClick={() => setEventPage(page => Math.max(page - 1, 0))}>
              ← Prev
            </button>
            <span style={{ color: '#718096', fontSize: 13 }}>
              Trang {eventPage + 1} / {eventTotalPages}
            </span>
            <button className="gv-btn" disabled={eventPage >= eventTotalPages - 1} onClick={() => setEventPage(page => Math.min(page + 1, eventTotalPages - 1))}>
              Next →
            </button>
          </div>
        )}
      </section>

      {selectedPayload && <PayloadModal payload={selectedPayload} onClose={() => setSelectedPayload(null)} />}

      {!loading && filteredDashboard.map(group => (
        <section key={group.eventType} className="settings-card" style={{ marginBottom: 16 }}>
          <div className="card-header">
            <EventTypeBadge type={group.eventType} />
            <span className="card-title" style={{ marginLeft: 8 }}>{group.eventType}</span>
            <span className="card-count">{group.topFields.length} trường</span>
          </div>

          {group.topFields.length === 0 ? (
            <div className="empty">Không có trường lạ</div>
          ) : (
            <div className="entity-table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Tên trường</th>
                    <th>Số lần</th>
                    <th>Giá trị mẫu</th>
                    <th>Event ID</th>
                    <th>Event IDs</th>
                    <th>Lần cuối</th>
                  </tr>
                </thead>
                <tbody>
                  {group.topFields.map((field, idx) => (
                    <tr key={field.fieldName}>
                      <td style={{ color: '#718096' }}>{idx + 1}</td>
                      <td>
                        <span className="mono" style={{ color: '#f6ad55', fontWeight: 600 }}>
                          {field.fieldName}
                        </span>
                      </td>
                      <td>
                        <span className="unknown-count-pill">
                          {field.count}
                        </span>
                      </td>
                      <td className="unknown-sample-cell">{field.sampleValue || '—'}</td>
                      <td className="incident-table-cve">{field.lastEventId || '—'}</td>
                      <td className="unknown-event-ids-cell">
                        {field.eventIds?.length > 0 ? (
                          <div className="unknown-event-tags">
                            {field.eventIds.map((eventId, eid) => (
                              <span key={`${field.fieldName}-${eid}`} className="unknown-event-chip">
                                {eventId}
                              </span>
                            ))}
                          </div>
                        ) : '—'}
                      </td>
                      <td className="incident-table-time">{field.lastSeen ? new Date(field.lastSeen).toLocaleString('vi-VN', { hour12: false }) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ))}
    </div>
  )
}
