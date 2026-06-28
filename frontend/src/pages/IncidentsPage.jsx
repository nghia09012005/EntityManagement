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

  useEffect(() => {
    fetchIncidentStats().then(setStats).catch(() => {})
  }, [])

  useEffect(() => {
    if (tab !== 'incidents') return
    setLoading(true)
    fetchIncidents(incidentPage, 20)
      .then(data => {
        setIncidents(data.content ?? [])
        setIncTotalPages(data.totalPages ?? 0)
      })
      .catch(() => setIncidents([]))
      .finally(() => setLoading(false))
  }, [tab, incidentPage])

  useEffect(() => {
    if (tab !== 'alerts') return
    setLoading(true)
    fetchAlertLogs(alertPage, 20)
      .then(data => {
        setAlertLogs(data.content ?? [])
        setAltTotalPages(data.totalPages ?? 0)
      })
      .catch(() => setAlertLogs([]))
      .finally(() => setLoading(false))
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

  return (
    <>
      {/* ── Stats section ─────────────────────────────────── */}
      {stats && (
        <div style={{ marginBottom: 24 }}>
          {/* Summary cards */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
            <StatCard label="Total Incidents" value={stats.totalIncidents} color="#a0aec0" />
            <StatCard label="Critical"        value={stats.bySeverity?.CRITICAL ?? 0} color="#fc8181" />
            <StatCard label="High"            value={stats.bySeverity?.HIGH ?? 0}     color="#f6ad55" />
            <StatCard label="New"             value={stats.byStatus?.NEW ?? 0}        color="#fc8181" />
            <StatCard label="Alert Logs"      value={stats.totalAlerts ?? 0}          color="#63b3ed" />
          </div>

          {/* Top IPs + Top Hosts */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div style={{ background: '#1a1d2e', border: '1px solid #2d3748', borderRadius: 8, padding: 16 }}>
              <div style={{ fontSize: 11, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 12 }}>
                Top IPs — bởi severity
              </div>
              {(stats.topIps ?? []).length === 0
                ? <div style={{ color: '#4a5568', fontSize: 13 }}>Chưa có dữ liệu</div>
                : (stats.topIps ?? []).map((item, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <span style={{ fontFamily: 'monospace', fontSize: 13, color: '#e2e8f0', flex: 1 }}>{item.ip}</span>
                    <SevBadge severity={item.severity} />
                    <span style={{ fontSize: 12, color: '#718096' }}>×{item.count}</span>
                  </div>
                ))
              }
            </div>

            <div style={{ background: '#1a1d2e', border: '1px solid #2d3748', borderRadius: 8, padding: 16 }}>
              <div style={{ fontSize: 11, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 12 }}>
                Top Hosts — hoạt động 24h
              </div>
              {(stats.topHosts ?? []).length === 0
                ? <div style={{ color: '#4a5568', fontSize: 13 }}>Chưa có dữ liệu</div>
                : (stats.topHosts ?? []).map((item, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <span style={{ fontFamily: 'monospace', fontSize: 13, color: '#e2e8f0', flex: 1 }}>{item.host}</span>
                    <span style={{ background: '#2d3748', borderRadius: 4, padding: '2px 8px', fontSize: 12, color: '#a0aec0' }}>
                      {item.count} events
                    </span>
                  </div>
                ))
              }
            </div>
          </div>
        </div>
      )}

      {/* ── Tabs + content ────────────────────────────────── */}
      <div className="card">
        {/* Tab strip */}
        <div style={{ display: 'flex', borderBottom: '1px solid #2d3748', padding: '0 16px' }}>
          {[
            { key: 'incidents', label: 'Incidents', count: stats?.totalIncidents },
            { key: 'alerts',    label: 'Alert Logs', count: stats?.totalAlerts },
          ].map(t => (
            <button key={t.key} onClick={() => setTab(t.key)} style={{
              background: 'none', border: 'none', cursor: 'pointer',
              padding: '12px 16px', fontSize: 13, fontWeight: 600,
              color: tab === t.key ? '#63b3ed' : '#718096',
              borderBottom: tab === t.key ? '2px solid #63b3ed' : '2px solid transparent',
              marginBottom: -1,
            }}>
              {t.label}{t.count != null ? ` (${t.count})` : ''}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="loading">Đang tải…</div>
        ) : tab === 'incidents' ? (
          <>
            {incidents.length === 0
              ? <div className="empty">Chưa phát hiện kịch bản tấn công nào.</div>
              : (
                <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {incidents.map(inc => (
                    <div key={inc.id} style={{
                      background: '#0f1220', border: '1px solid #2d3748', borderRadius: 8, overflow: 'hidden',
                    }}>
                      {/* Header row */}
                      <div
                        onClick={() => setExpanded(expanded === inc.id ? null : inc.id)}
                        style={{ padding: '12px 16px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}
                      >
                        <SevBadge severity={inc.severity} />
                        <MitreBadge mitreId={inc.mitreId} />
                        <span style={{ flex: 1, fontSize: 14, fontWeight: 600, color: '#e2e8f0', minWidth: 200 }}>
                          {inc.title}
                        </span>
                        <span style={{ fontSize: 12, color: '#718096' }}>{fmt(inc.detectedAt)}</span>
                        <StatusBadge status={inc.status} />
                        <span style={{ color: '#4a5568', fontSize: 12 }}>{expanded === inc.id ? '▲' : '▼'}</span>
                      </div>

                      {/* Expanded body */}
                      {expanded === inc.id && (
                        <div style={{ borderTop: '1px solid #1e2235', padding: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
                          {/* Timeline */}
                          <div>
                            <div style={{ fontSize: 11, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 10 }}>
                              Timeline
                            </div>
                            {(inc.timeline ?? []).map((t, i) => (
                              <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 7, fontSize: 12 }}>
                                <span style={{ color: '#4a5568', whiteSpace: 'nowrap', minWidth: 65 }}>
                                  {fmtTime(t.time)}
                                </span>
                                <span style={{ color: '#63b3ed' }}>●</span>
                                <span style={{ color: '#a0aec0' }}>{t.summary}</span>
                              </div>
                            ))}
                          </div>

                          {/* Right panel */}
                          <div>
                            <div style={{ fontSize: 11, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 10 }}>
                              Recommended Actions
                            </div>
                            {(inc.recommendedActions ?? []).map((action, i) => (
                              <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 6, fontSize: 12, color: '#a0aec0' }}>
                                <span style={{ color: '#63b3ed', flexShrink: 0 }}>○</span>
                                <span>{action}</span>
                              </div>
                            ))}

                            {/* Affected entities */}
                            {inc.affectedEntities && Object.keys(inc.affectedEntities).length > 0 && (
                              <div style={{ marginTop: 14, padding: '10px 12px', background: '#1a1d2e', borderRadius: 6 }}>
                                <div style={{ fontSize: 11, color: '#718096', marginBottom: 6 }}>Affected Entities</div>
                                {Object.entries(inc.affectedEntities).map(([type, values]) => (
                                  <div key={type} style={{ fontSize: 12, marginBottom: 3 }}>
                                    <span style={{ color: '#718096' }}>{type}: </span>
                                    <span style={{ color: '#e2e8f0', fontFamily: 'monospace' }}>
                                      {(values ?? []).join(', ')}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            )}

                            {/* Status selector */}
                            <div style={{ marginTop: 14 }}>
                              <div style={{ fontSize: 11, color: '#718096', marginBottom: 6 }}>Update Status</div>
                              <select
                                value={inc.status}
                                onChange={e => handleStatusChange(inc.id, e.target.value)}
                                style={{
                                  background: '#1a1d2e', border: '1px solid #2d3748', borderRadius: 4,
                                  color: '#e2e8f0', padding: '5px 10px', fontSize: 12, cursor: 'pointer',
                                }}
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
              )
            }
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
                          <td style={{ color: '#718096', fontSize: 13 }}>{fmt(log.timestamp)}</td>
                          <td style={{ fontWeight: 600, color: '#e2e8f0' }}>{rd.alertName ?? '—'}</td>
                          <td>{rd.severity ? <SevBadge severity={rd.severity} /> : '—'}</td>
                          <td style={{ fontFamily: 'monospace', fontSize: 12, color: '#a0aec0' }}>{rd.targetIp ?? '—'}</td>
                          <td style={{ fontFamily: 'monospace', fontSize: 12, color: '#a0aec0' }}>{rd.targetHost ?? '—'}</td>
                          <td style={{ fontFamily: 'monospace', fontSize: 12, color: '#63b3ed' }}>{rd.targetCve ?? '—'}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              )
            }
            <Pagination
              page={alertPage} totalPages={altTotalPages}
              onPrev={() => setAlertPage(p => p - 1)}
              onNext={() => setAlertPage(p => p + 1)}
            />
          </>
        )}
      </div>
    </>
  )
}