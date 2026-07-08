import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ENTITY_TYPES, ENTITY_LABELS, listEntities, fetchLogByEventId, fetchLogStats, fetchIncidents } from '../api'
import EntityBadge from '../components/EntityBadge'
import { LineChartCard, PieChartCard, TimeRangeToggle } from '../components/StatsCharts'
import { getUnresolvedIncidentEntityValues } from '../utils/incidents'

const ID_PROP = {
  user:          'username',
  host:          'hostname',
  ip:            'address',
  domain:        'name',
  filehash:      'hash',
  url:           'url',
  process:       'name',
  cloudresource: 'resourceId',
  email:         'address',
  cve:           'cveId',
}

const COLUMNS = {
  user:          ['username'],
  host:          ['hostname'],
  ip:            ['address'],
  domain:        ['name'],
  filehash:      ['hash'],
  url:           ['url'],
  process:       ['name', 'path'],
  cloudresource: ['resourceId'],
  email:         ['address'],
  cve:           ['cveId'],
}

export default function EntityListPage() {
  const [active, setActive] = useState('user')
  const [data, setData]     = useState([])
  const [loading, setLoading] = useState(false)
  const [eventId, setEventId] = useState('')
  const [eventLog, setEventLog] = useState(null)
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [entityQuery, setEntityQuery] = useState('')
  const [timestampFilter, setTimestampFilter] = useState('')
  const [chartStats, setChartStats] = useState({ line: [], pie: [] })
  const [chartRange, setChartRange] = useState('24h')
  const [chartLoading, setChartLoading] = useState(true)
  const [incidentAlertValues, setIncidentAlertValues] = useState([])
  const navigate = useNavigate()

  useEffect(() => {
    let activeRequest = true
    const loadEntities = async () => {
      try {
        setLoading(true)
        setData([])
        const result = await listEntities(active)
        if (!activeRequest) return
        setData(result)
      } catch {
        if (!activeRequest) return
        setData([])
      } finally {
        if (activeRequest) setLoading(false)
      }
    }
    loadEntities()
    return () => { activeRequest = false }
  }, [active])

  useEffect(() => {
    let activeRequest = true
    const loadStats = async () => {
      try {
        setChartLoading(true)
        const data = await fetchLogStats(chartRange)
        if (!activeRequest) return
        setChartStats({ line: data.line || [], pie: data.pie || [] })
      } catch {
        if (!activeRequest) return
        setChartStats({ line: [], pie: [] })
      } finally {
        if (activeRequest) setChartLoading(false)
      }
    }
    loadStats()
    return () => { activeRequest = false }
  }, [chartRange])

  useEffect(() => {
    let activeRequest = true
    const loadIncidents = async () => {
      try {
        const data = await fetchIncidents(0, 100)
        if (!activeRequest) return
        const unresolvedValues = getUnresolvedIncidentEntityValues(data.content || [], active)
        setIncidentAlertValues(Array.from(unresolvedValues))
      } catch {
        if (!activeRequest) return
        setIncidentAlertValues([])
      }
    }
    loadIncidents()
    return () => { activeRequest = false }
  }, [active])

  const searchByEventId = async () => {
    const id = eventId.trim()
    if (!id) return
    setSearching(true)
    setSearchError('')
    setEventLog(null)
    try {
      const result = await fetchLogByEventId(id)
      setEventLog(result)
    } catch (e) {
      setSearchError(e.message)
    } finally {
      setSearching(false)
    }
  }

  const cols = COLUMNS[active] || []
  const idProp = ID_PROP[active]
  const q = entityQuery.trim().toLowerCase()
  const ts = timestampFilter.trim()
  const filteredData = data.filter((node) => {
    const p = node.properties || {}
    const matchesQuery = !q || cols.some((c) => String(p[c] ?? '').toLowerCase().includes(q))
    const matchesTimestamp = !ts || (() => {
      const raw = p.timestamp || p.lastSeen || p.detectedAt || p.occurredAt || ''
      if (!raw) return false
      return String(raw).toLowerCase().includes(ts.toLowerCase())
    })()
    return matchesQuery && matchesTimestamp
  })
  const activeIncidentAlertSet = new Set(incidentAlertValues)

  return (
    <div className="entity-shell">
      <section className="entity-hero">
        <div>
          <div className="settings-kicker">Entity Explorer</div>
          <div className="page-title">Entities & Event Search</div>
          <div className="page-sub">Tra cứu eventId, xem raw payload và duyệt entity theo từng loại để theo dõi hoạt động SOC.</div>
        </div>
        <div className="settings-hero-actions">
          <div className="settings-pill">📦 {data.length} entities</div>
          <div className="settings-pill">⚠️ {incidentAlertValues.length} flagged</div>
        </div>
      </section>

      <section className="entity-search-card">
        <div className="card-header">
          <span className="card-title">Search log by Event ID</span>
        </div>
        <div className="entity-search-row">
          <input
            className="pf-input"
            value={eventId}
            onChange={(e) => setEventId(e.target.value)}
            placeholder="Paste eventId / uid here"
            onKeyDown={(e) => { if (e.key === 'Enter') searchByEventId() }}
          />
          <button className="pf-run-btn" onClick={searchByEventId} disabled={searching || !eventId.trim()}>
            {searching ? 'Searching…' : 'Search'}
          </button>
        </div>
        {searchError && <div className="pf-error" style={{ marginTop: 12 }}>{searchError}</div>}
        {eventLog && (
          <div className="entity-log-panel">
            <div className="prop-list">
              <div className="prop-row"><span className="prop-key">eventId</span><span className="prop-val mono">{eventLog.eventId}</span></div>
              <div className="prop-row"><span className="prop-key">category</span><span className="prop-val">{eventLog.category || '—'}</span></div>
              <div className="prop-row"><span className="prop-key">source</span><span className="prop-val">{eventLog.source || '—'}</span></div>
              <div className="prop-row"><span className="prop-key">timestamp</span><span className="prop-val">{eventLog.timestamp || '—'}</span></div>
              <div className="prop-row"><span className="prop-key">tenantId</span><span className="prop-val">{eventLog.tenantId || '—'}</span></div>
            </div>
            <div className="entity-log-block">
              <div className="entity-log-label">rawData</div>
              <pre>{JSON.stringify(eventLog.rawData || {}, null, 2)}</pre>
            </div>
            <div className="entity-log-block">
              <div className="entity-log-label">rawEvent</div>
              <pre>{eventLog.rawEvent || '—'}</pre>
            </div>
          </div>
        )}
      </section>

      <div className="chart-toolbar">
        <div>
          <div className="chart-toolbar-label">Khoảng thời gian</div>
          <div className="chart-toolbar-hint">Biểu đồ log theo dữ liệu gần nhất</div>
        </div>
        <TimeRangeToggle value={chartRange} onChange={setChartRange} />
      </div>

      <div className="grid-2" style={{ marginBottom: 20, alignItems: 'stretch' }}>
        <LineChartCard
          title="Log count over time"
          subtitle={`Dữ liệu ${chartRange}`}
          data={chartStats.line}
          timeRange={chartRange}
          loading={chartLoading}
        />
        <PieChartCard
          title="Log by eventType"
          subtitle={`Tỷ lệ eventType · ${chartRange}`}
          data={chartStats.pie}
          loading={chartLoading}
        />
      </div>

      <div className="tabs">
        {ENTITY_TYPES.map(t => (
          <div
            key={t}
            className={`tab ${active === t ? 'active' : ''}`}
            onClick={() => setActive(t)}
          >
            {ENTITY_LABELS[t]}
          </div>
        ))}
      </div>

      <section className="settings-card">
        <div className="card-header">
          <EntityBadge label={ENTITY_LABELS[active]} />
          <span className="card-title">Entity List</span>
          <span className="card-count">{filteredData.length} / {data.length} entities</span>
        </div>

        <div className="entity-filter-bar">
          <input
            className="pf-input"
            value={entityQuery}
            onChange={(e) => setEntityQuery(e.target.value)}
            placeholder={`Tìm ${ENTITY_LABELS[active].toLowerCase()} theo giá trị...`}
          />
          <input
            className="pf-input"
            value={timestampFilter}
            onChange={(e) => setTimestampFilter(e.target.value)}
            placeholder="Lọc theo timestamp (vd: 2024-06)"
          />
          {(entityQuery || timestampFilter) && (
            <button className="gv-btn" onClick={() => { setEntityQuery(''); setTimestampFilter('') }} style={{ color: '#fc8181' }}>✕ Xoá</button>
          )}
        </div>

        {loading ? (
          <div className="loading">Đang tải…</div>
        ) : data.length === 0 ? (
          <div className="empty">Không có dữ liệu — hãy upload log file trước.</div>
        ) : filteredData.length === 0 ? (
          <div className="empty">Không tìm thấy entity nào phù hợp với từ khoá.</div>
        ) : (
          <div className="entity-table-wrapper">
            <table>
              <thead>
                <tr>
                  {cols.map(c => <th key={c}>{c}</th>)}
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredData.map((node) => {
                  const p = node.properties || {}
                  const idVal = p[idProp]
                  const entityValue = String(idVal ?? '').trim().toLowerCase()
                  const isAlerted = Boolean(entityValue && activeIncidentAlertSet.has(entityValue))
                  return (
                    <tr
                      key={node.id}
                      className={isAlerted ? 'entity-alert-row' : ''}
                    >
                      {cols.map(c => (
                        <td key={c}>
                          {c === 'hash'
                            ? <span className="mono">{p[c]}</span>
                            : (
                              <div className="entity-cell">
                                <span>{String(p[c] ?? '—')}</span>
                                {isAlerted && c === idProp && (
                                  <span className="entity-incident-badge">
                                    ⚠ Active incident
                                  </span>
                                )}
                              </div>
                            )
                          }
                        </td>
                      ))}
                      <td>
                        <span
                          className="link"
                          onClick={() => navigate(`/entity/${active}/${encodeURIComponent(idVal)}`)}
                        >
                          View detail →
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
