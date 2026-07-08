import { useState, useEffect } from 'react'
import { fetchIncidents, fetchIncidentStats, fetchAlertLogs, updateIncidentStatus } from '../api'

const SEV_STYLE = {
  CRITICAL: { bg: '#3a1a1a', color: '#fc8181' },
  HIGH:     { bg: '#3a2a1a', color: '#f6ad55' },
  MEDIUM:   { bg: '#3a381a', color: '#fbd38d' },
  LOW:      { bg: '#1a3a25', color: '#68d391' },
}

const STATUS_STYLE = {
  NEW:           { bg: '#3a1a1a', color: '#fc8181' },
  INVESTIGATING: { bg: '#3a2a1a', color: '#f6ad55' },
  RESOLVED:      { bg: '#1a3a25', color: '#68d391' },
}

function SevBadge({ severity }) {
  const s = SEV_STYLE[severity] || { bg: '#2d2d2d', color: '#a0aec0' }
  return (
    <span style={{
      background: s.bg, color: s.color,
      padding: '2px 8px', borderRadius: 999, fontSize: 11, fontWeight: 700,
      textTransform: 'uppercase', letterSpacing: '0.4px', whiteSpace: 'nowrap',
    }}>
      {severity}
    </span>
  )
}

function StatusBadge({ status }) {
  const s = STATUS_STYLE[status] || { bg: '#2d2d2d', color: '#a0aec0' }
  return (
    <span style={{
      background: s.bg, color: s.color,
      padding: '2px 8px', borderRadius: 999, fontSize: 11, fontWeight: 600,
      whiteSpace: 'nowrap',
    }}>
      {status}
    </span>
  )
}

function MitreBadge({ mitreId }) {
  if (!mitreId) return null
  return (
    <span style={{
      background: '#1a2d3a', color: '#63b3ed',
      padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600,
      fontFamily: 'monospace', whiteSpace: 'nowrap',
    }}>
      {mitreId}
    </span>
  )
}

function StatCard({ label, value, color }) {
  return (
    <div style={{
      background: '#1a1d2e', border: '1px solid #2d3748', borderRadius: 8,
      padding: '12px 20px', minWidth: 120,
    }}>
      <div style={{ fontSize: 11, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 24, fontWeight: 700, color: value > 0 ? color : '#a0aec0' }}>
        {value ?? 0}
      </div>
    </div>
  )
}

function Pagination({ page, totalPages, onPrev, onNext }) {
  if (totalPages <= 1) return null
  return (
    <div style={{ display: 'flex', gap: 8, padding: '12px 16px', borderTop: '1px solid #1e2235', alignItems: 'center' }}>
      <button className="gv-btn" disabled={page === 0} onClick={onPrev}>← Prev</button>
      <span style={{ fontSize: 13, color: '#718096' }}>Page {page + 1} / {totalPages}</span>
      <button className="gv-btn" disabled={page >= totalPages - 1} onClick={onNext}>Next →</button>
    </div>
  )
}

export default function IncidentsPage() {
  const [stats, setStats]                 = useState(null)
  const [incidents, setIncidents]         = useState([])
  const [alertLogs, setAlertLogs]         = useState([])
  const [tab, setTab]                     = useState('incidents')
  const [incidentPage, setIncidentPage]   = useState(0)
  const [alertPage, setAlertPage]         = useState(0)
  const [incTotalPages, setIncTotalPages] = useState(0)
  const [altTotalPages, setAltTotalPages] = useState(0)
  const [loading, setLoading]             = useState(false)
  const [expanded, setExpanded]           = useState(null)
  const [incidentFilter, setIncidentFilter] = useState('')

  useEffect(() => {
    fetchIncidentStats().then(setStats).catch(() => {})
  }, [])

  useEffect(() => {
    if (tab !== 'incidents') return
    let activeRequest = true
    const loadIncidents = async () => {
      try {
        setLoading(true)
        const data = await fetchIncidents(incidentPage, 20)
        if (!activeRequest) return
        setIncidents(data.content ?? [])
        setIncTotalPages(data.totalPages ?? 0)
      } catch {
        if (!activeRequest) return
        setIncidents([])
      } finally {
        if (activeRequest) setLoading(false)
      }
    }
    loadIncidents()
    return () => { activeRequest = false }
  }, [tab, incidentPage])

  useEffect(() => {
    if (tab !== 'alerts') return
    let activeRequest = true
    const loadAlerts = async () => {
      try {
        setLoading(true)
        const data = await fetchAlertLogs(alertPage, 20)
        if (!activeRequest) return
        setAlertLogs(data.content ?? [])
        setAltTotalPages(data.totalPages ?? 0)
      } catch {
        if (!activeRequest) return
        setAlertLogs([])
      } finally {
        if (activeRequest) setLoading(false)
      }
    }
    loadAlerts()
    return () => { activeRequest = false }
  }, [tab, alertPage])

  const handleStatusChange = async (id, status) => {
    try {
      await updateIncidentStatus(id, status)
      setIncidents(prev => prev.map(i => i.id === id ? { ...i, status } : i))
    } catch (e) {
      console.error('Status update failed', e)
    }
  }

  const fmt = iso => {
    if (!iso) return '—'
    try { return new Date(iso).toLocaleString('vi-VN', { hour12: false }) } catch { return iso }
  }

  const fmtTime = iso => {
    if (!iso) return ''
    try { return new Date(iso).toLocaleTimeString('vi-VN', { hour12: false }) } catch { return iso }
  }

  const filteredIncidents = !incidentFilter
    ? incidents
    : incidents.filter(inc => {
        const haystack = `${inc.title || ''} ${inc.detectedAt || ''} ${inc.status || ''}`.toLowerCase()
        return haystack.includes(incidentFilter.toLowerCase())
      })

  return (
    <div className="incident-shell">
      <section className="incident-hero">
        <div>
          <div className="settings-kicker">Incident Center</div>
          <div className="page-title">Threat Monitoring & Response</div>
          <div className="page-sub">Theo dõi incident, xem timeline và cập nhật trạng thái xử lý trực tiếp từ dashboard.</div>
        </div>
        <div className="settings-hero-actions">
          <div className="settings-pill">🚨 {stats?.totalIncidents ?? 0} incidents</div>
          <div className="settings-pill">📡 {stats?.totalAlerts ?? 0} alert logs</div>
        </div>
      </section>

      {stats && (
        <div className="incident-summary">
          <div className="incident-stats-grid">
            <StatCard label="Total Incidents" value={stats.totalIncidents} color="#a0aec0" />
            <StatCard label="Critical" value={stats.bySeverity?.CRITICAL ?? 0} color="#fc8181" />
            <StatCard label="High" value={stats.bySeverity?.HIGH ?? 0} color="#f6ad55" />
            <StatCard label="New" value={stats.byStatus?.NEW ?? 0} color="#fc8181" />
            <StatCard label="Alert Logs" value={stats.totalAlerts ?? 0} color="#63b3ed" />
          </div>

          <div className="incident-insight-grid">
            <div className="incident-insight-card">
              <div className="incident-insight-card__title">Top IPs — bởi severity</div>
              {(stats.topIps ?? []).length === 0
                ? <div className="settings-empty-state">Chưa có dữ liệu</div>
                : (stats.topIps ?? []).map((item, i) => (
                  <div key={i} className="incident-insight-row">
                    <span className="incident-insight-row__value">{item.ip}</span>
                    <SevBadge severity={item.severity} />
                    <span className="incident-insight-row__count">×{item.count}</span>
                  </div>
                ))}
            </div>

            <div className="incident-insight-card">
              <div className="incident-insight-card__title">Top Hosts — hoạt động 24h</div>
              {(stats.topHosts ?? []).length === 0
                ? <div className="settings-empty-state">Chưa có dữ liệu</div>
                : (stats.topHosts ?? []).map((item, i) => (
                  <div key={i} className="incident-insight-row">
                    <span className="incident-insight-row__value">{item.host}</span>
                    <span className="incident-insight-row__pill">{item.count} events</span>
                  </div>
                ))}
            </div>
          </div>
        </div>
      )}

      <section className="settings-card">
        <div className="incident-tab-list">
          {[
            { key: 'incidents', label: 'Incidents', count: stats?.totalIncidents },
            { key: 'alerts', label: 'Alert Logs', count: stats?.totalAlerts },
          ].map(t => (
            <button key={t.key} onClick={() => setTab(t.key)} className={`incident-tab ${tab === t.key ? 'active' : ''}`}>
              {t.label}{t.count != null ? ` (${t.count})` : ''}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="loading">Đang tải…</div>
        ) : tab === 'incidents' ? (
          <>
            <div className="entity-filter-bar">
              <input
                className="pf-input"
                value={incidentFilter}
                onChange={(e) => setIncidentFilter(e.target.value)}
                placeholder="Lọc theo thời gian / tiêu đề incident"
              />
              {incidentFilter && (
                <button className="gv-btn" onClick={() => setIncidentFilter('')} style={{ color: '#fc8181' }}>✕ Xoá</button>
              )}
            </div>
            {filteredIncidents.length === 0
              ? <div className="empty">Chưa phát hiện kịch bản tấn công nào.</div>
              : (
                <div className="incident-list-stack">
                  {filteredIncidents.map(inc => (
                    <div key={inc.id} className="incident-list-card">
                      <div
                        onClick={() => setExpanded(expanded === inc.id ? null : inc.id)}
                        className="incident-list-card__header"
                      >
                        <SevBadge severity={inc.severity} />
                        <MitreBadge mitreId={inc.mitreId} />
                        <span className="incident-list-card__title">{inc.title}</span>
                        <span className="incident-list-card__time">{fmt(inc.detectedAt)}</span>
                        <StatusBadge status={inc.status} />
                        <span className="incident-list-card__toggle">{expanded === inc.id ? '▲' : '▼'}</span>
                      </div>

                      {expanded === inc.id && (
                        <div className="incident-expanded-grid">
                          <div>
                            <div className="incident-section-label">Timeline</div>
                            {(inc.timeline ?? []).map((t, i) => (
                              <div key={i} className="incident-timeline-item">
                                <span className="incident-timeline-time">{fmtTime(t.time)}</span>
                                <span className="incident-timeline-dot">●</span>
                                <div className="incident-timeline-content">
                                  <span>{t.summary}</span>
                                  {t.eventId && (
                                    <span className="incident-timeline-event">eventId: {t.eventId}</span>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>

                          <div>
                            <div className="incident-section-label">Recommended Actions</div>
                            {(inc.recommendedActions ?? []).map((action, i) => (
                              <div key={i} className="incident-action-item">
                                <span className="incident-action-dot">○</span>
                                <span>{action}</span>
                              </div>
                            ))}

                            {inc.affectedEntities && Object.keys(inc.affectedEntities).length > 0 && (
                              <div className="incident-entity-box">
                                <div className="incident-section-label">Affected Entities</div>
                                {Object.entries(inc.affectedEntities).map(([type, values]) => (
                                  <div key={type} className="incident-entity-row">
                                    <span className="incident-entity-row__type">{type}: </span>
                                    <span className="incident-entity-row__value">{(values ?? []).join(', ')}</span>
                                  </div>
                                ))}
                              </div>
                            )}

                            <div className="incident-status-box">
                              <div className="incident-section-label">Update Status</div>
                              <select
                                value={inc.status}
                                onChange={e => handleStatusChange(inc.id, e.target.value)}
                                className="pf-select"
                              >
                                <option value="NEW">NEW</option>
                                <option value="INVESTIGATING">INVESTIGATING</option>
                                <option value="RESOLVED">RESOLVED</option>
                              </select>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            <Pagination
              page={incidentPage} totalPages={incTotalPages}
              onPrev={() => setIncidentPage(p => p - 1)}
              onNext={() => setIncidentPage(p => p + 1)}
            />
          </>
        ) : (
          <>
            {alertLogs.length === 0
              ? <div className="empty">Không có alert log nào.</div>
              : (
                <div className="incident-table-wrapper">
                  <table>
                    <thead>
                      <tr>
                        <th>Time</th>
                        <th>Alert Name</th>
                        <th>Severity</th>
                        <th>Target IP</th>
                        <th>Target Host</th>
                        <th>CVE</th>
                      </tr>
                    </thead>
                    <tbody>
                      {alertLogs.map(log => {
                        const rd = log.rawData ?? {}
                        return (
                          <tr key={log.eventId}>
                            <td className="incident-table-time">{fmt(log.timestamp)}</td>
                            <td className="incident-table-title">{rd.alertName ?? '—'}</td>
                            <td>{rd.severity ? <SevBadge severity={rd.severity} /> : '—'}</td>
                            <td className="incident-table-mono">{rd.targetIp ?? '—'}</td>
                            <td className="incident-table-mono">{rd.targetHost ?? '—'}</td>
                            <td className="incident-table-cve">{rd.targetCve ?? '—'}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            <Pagination
              page={alertPage} totalPages={altTotalPages}
              onPrev={() => setAlertPage(p => p - 1)}
              onNext={() => setAlertPage(p => p + 1)}
            />
          </>
        )}
      </section>
    </div>
  )
}