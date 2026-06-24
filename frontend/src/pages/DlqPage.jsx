import { useState, useEffect } from 'react'
import { fetchDlqEvents, fetchDlqSummary } from '../api'

const TOPIC_COLOR = {
  'raw-logs':          { bg: '#3a1a1a', color: '#fc8181' },
  'normalized-events': { bg: '#1a2d3a', color: '#63b3ed' },
  'enriched-events':   { bg: '#1a2d1a', color: '#68d391' },
}

function TopicBadge({ topic }) {
  const style = TOPIC_COLOR[topic] || { bg: '#2d2d2d', color: '#a0aec0' }
  return (
    <span style={{
      background: style.bg, color: style.color,
      padding: '2px 8px', borderRadius: 999, fontSize: 11,
      fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.4px',
      whiteSpace: 'nowrap',
    }}>
      {topic}
    </span>
  )
}

function PayloadModal({ payload, onClose }) {
  let formatted = payload
  try { formatted = JSON.stringify(JSON.parse(payload), null, 2) } catch (_) {}
  return (
    <div onClick={onClose} style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 999,
    }}>
      <div onClick={e => e.stopPropagation()} style={{
        background: '#1a1d2e', border: '1px solid #2d3748', borderRadius: 10,
        width: '80vw', maxWidth: 800, maxHeight: '70vh',
        display: 'flex', flexDirection: 'column',
      }}>
        <div style={{
          padding: '12px 16px', borderBottom: '1px solid #2d3748',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <span style={{ fontWeight: 600, fontSize: 14 }}>Original Payload</span>
          <span onClick={onClose} style={{ cursor: 'pointer', color: '#718096', fontSize: 18 }}>✕</span>
        </div>
        <pre style={{
          overflow: 'auto', padding: 16, margin: 0,
          fontSize: 12, color: '#a0aec0', lineHeight: 1.6,
          fontFamily: 'monospace',
        }}>
          {formatted || '(empty)'}
        </pre>
      </div>
    </div>
  )
}

export default function DlqPage() {
  const [summary, setSummary]     = useState(null)
  const [events, setEvents]       = useState([])
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage]           = useState(0)
  const [loading, setLoading]     = useState(false)
  const [payload, setPayload]     = useState(null)

  useEffect(() => {
    fetchDlqSummary().then(setSummary).catch(() => setSummary(null))
  }, [])

  useEffect(() => {
    setLoading(true)
    fetchDlqEvents(page, 20)
      .then(data => {
        setEvents(data.content ?? [])
        setTotalPages(data.totalPages ?? 0)
      })
      .catch(() => setEvents([]))
      .finally(() => setLoading(false))
  }, [page])

  const fmt = (iso) => {
    if (!iso) return '—'
    const d = new Date(iso)
    return d.toLocaleString('vi-VN', { hour12: false })
  }

  return (
    <>
      {payload && <PayloadModal payload={payload} onClose={() => setPayload(null)} />}

      {/* Summary stats */}
      {summary && (
        <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
          {[
            { label: 'Total Failed', value: summary.total, color: '#fc8181' },
            { label: 'raw-logs',          value: summary['raw-logs'],          color: '#fc8181' },
            { label: 'normalized-events', value: summary['normalized-events'], color: '#63b3ed' },
            { label: 'enriched-events',   value: summary['enriched-events'],   color: '#68d391' },
          ].map(s => (
            <div key={s.label} style={{
              background: '#1a1d2e', border: '1px solid #2d3748', borderRadius: 8,
              padding: '12px 20px', minWidth: 140,
            }}>
              <div style={{ fontSize: 11, color: '#718096', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>
                {s.label}
              </div>
              <div style={{ fontSize: 24, fontWeight: 700, color: s.value > 0 ? s.color : '#a0aec0' }}>
                {s.value ?? 0}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Event table */}
      <div className="card">
        <div className="card-header">
          <span style={{ fontSize: 16 }}>☠</span>
          <span className="card-title">Dead Letter Events</span>
          <span className="card-count">{summary?.total ?? 0} total</span>
        </div>

        {loading ? (
          <div className="loading">Đang tải…</div>
        ) : events.length === 0 ? (
          <div className="empty">Không có event nào thất bại.</div>
        ) : (
          <>
            <table>
              <thead>
                <tr>
                  <th>Source Topic</th>
                  <th>Error Class</th>
                  <th>Error Message</th>
                  <th>Failed At</th>
                  <th>Payload</th>
                </tr>
              </thead>
              <tbody>
                {events.map(ev => (
                  <tr key={ev.id}>
                    <td><TopicBadge topic={ev.sourceTopic} /></td>
                    <td><span className="mono" style={{ color: '#fc8181' }}>{ev.errorClass}</span></td>
                    <td style={{ maxWidth: 340, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#a0aec0' }}>
                      {ev.error}
                    </td>
                    <td style={{ color: '#718096', fontSize: 13 }}>{fmt(ev.failedAt)}</td>
                    <td>
                      <span
                        className="link"
                        style={{ fontSize: 13 }}
                        onClick={() => setPayload(ev.originalPayload)}
                      >
                        View →
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination */}
            {totalPages > 1 && (
              <div style={{ display: 'flex', gap: 8, padding: '12px 16px', borderTop: '1px solid #1e2235', alignItems: 'center' }}>
                <button
                  className="gv-btn"
                  disabled={page === 0}
                  onClick={() => setPage(p => p - 1)}
                >← Prev</button>
                <span style={{ fontSize: 13, color: '#718096' }}>
                  Page {page + 1} / {totalPages}
                </span>
                <button
                  className="gv-btn"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(p => p + 1)}
                >Next →</button>
              </div>
            )}
          </>
        )}
      </div>
    </>
  )
}
