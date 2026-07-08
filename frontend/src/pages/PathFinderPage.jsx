import { useState, useEffect } from 'react'
import { findPath, createIncidentFromPath, ENTITY_TYPES, ENTITY_LABELS } from '../api'
import GraphView from '../components/GraphView'

const PLACEHOLDER = {
  user: 'admin', host: 'DC01', ip: '192.168.1.1',
  domain: 'evil.com', filehash: 'abc123…',
}

function EntityPicker({ label, type, setType, value, setValue }) {
  return (
    <div className="pf-picker">
      <div className="pf-picker-label">{label}</div>
      <select
        className="pf-select"
        value={type}
        onChange={e => setType(e.target.value)}
      >
        {ENTITY_TYPES.map(t => (
          <option key={t} value={t}>{ENTITY_LABELS[t]}</option>
        ))}
      </select>
      <input
        className="pf-input"
        value={value}
        onChange={e => setValue(e.target.value)}
        placeholder={PLACEHOLDER[type] || '…'}
      />
    </div>
  )
}

export default function PathFinderPage() {
  const [fromType,  setFromType]  = useState('user')
  const [fromValue, setFromValue] = useState('')
  const [toType,    setToType]    = useState('ip')
  const [toValue,   setToValue]   = useState('')
  const [maxHops,   setMaxHops]   = useState(6)
  const [mode,      setMode]      = useState('shortest')

  const [result,  setResult]  = useState(null)   // PathResponse
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState('')
  const [selectedPathIndex, setSelectedPathIndex] = useState(0)
  const [incidentTitle, setIncidentTitle] = useState('')
  const [incidentSeverity, setIncidentSeverity] = useState('MEDIUM')
  const [creatingIncident, setCreatingIncident] = useState(false)
  const [createResult, setCreateResult] = useState(null)
  const [createError, setCreateError] = useState('')

  useEffect(() => {
    if (result?.paths?.length) {
      setSelectedPathIndex(0)
    }
  }, [result])

  const run = async () => {
    if (!fromValue.trim() || !toValue.trim()) {
      setError('Nhập giá trị cho cả hai entity.')
      return
    }
    setError('')
    setResult(null)
    setLoading(true)
    try {
      const data = await findPath(fromType, fromValue.trim(), toType, toValue.trim(), maxHops, mode)
      setResult(data)
    } catch (e) {
      setError(`Lỗi: ${e.message}`)
    } finally {
      setLoading(false)
    }
  }

  const createIncident = async () => {
    if (!result?.paths?.length) return
    const path = result.paths[selectedPathIndex]
    if (!path || path.length === 0) {
      setCreateError('Chưa chọn đường đi hợp lệ.')
      return
    }
    setCreatingIncident(true)
    setCreateError('')
    setCreateResult(null)
    try {
      const response = await createIncidentFromPath({
        title: incidentTitle || `Incident from path ${selectedPathIndex + 1}`,
        severity: incidentSeverity,
        path,
      })
      setCreateResult(response)
    } catch (e) {
      setCreateError(e.message)
    } finally {
      setCreatingIncident(false)
    }
  }

  return (
    <>
      <div className="page-title">Path Finder</div>
      <div className="page-sub">Tìm đường đi ngắn nhất giữa 2 entity trong graph</div>

      {/* ── Query form ── */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-header">
          <span className="card-title">Tìm đường</span>
        </div>
        <div className="pf-form">
          <EntityPicker label="Từ" type={fromType} setType={setFromType}
                        value={fromValue} setValue={setFromValue} />

          <div className="pf-arrow">→</div>

          <EntityPicker label="Đến" type={toType} setType={setToType}
                        value={toValue} setValue={setToValue} />

          {/* Options */}
          <div className="pf-options">
            <div className="pf-opt-row">
              <span className="pf-opt-label">Max hops</span>
              {[2, 3, 4, 6, 8].map(h => (
                <button key={h}
                  className={`gv-btn ${maxHops === h ? 'active' : ''}`}
                  onClick={() => setMaxHops(h)}>{h}</button>
              ))}
            </div>
            <div className="pf-opt-row">
              <span className="pf-opt-label">Mode</span>
              <button className={`gv-btn ${mode === 'shortest' ? 'active' : ''}`}
                      onClick={() => setMode('shortest')}>Shortest</button>
              <button className={`gv-btn ${mode === 'all' ? 'active' : ''}`}
                      onClick={() => setMode('all')}>All shortest</button>
            </div>
          </div>

          <button className="pf-run-btn" onClick={run} disabled={loading}>
            {loading ? 'Đang tìm…' : 'Find Path'}
          </button>
        </div>

        {error && <div className="pf-error">{error}</div>}
      </div>

      {/* ── Result ── */}
      {result && (
        result.found ? (
          <div className="card">
            <div className="card-header">
              <span className="card-title">Kết quả</span>
              <span className="card-count">
                {result.pathCount} path · shortest = {result.shortestLength} hop ·{' '}
                {result.nodes.length} nodes · {result.edges.length} edges
              </span>
            </div>

            {result.paths?.length > 0 && (
              <div style={{ padding: '16px', borderBottom: '1px solid #1e2432', marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#e2e8f0' }}>Chọn đường để tạo incident</div>
                    <div style={{ marginTop: 4, color: '#a0aec0', fontSize: 12 }}>
                      {result.paths.length} đường tìm được. Chọn một đường và nhấn tạo incident.
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {result.paths.map((path, idx) => (
                      <button
                        key={idx}
                        type="button"
                        className={`gv-btn ${idx === selectedPathIndex ? 'active' : ''}`}
                        onClick={() => { setSelectedPathIndex(idx); setCreateResult(null); setCreateError('') }}
                      >
                        Path {idx + 1} ({path.length - 1} hops)
                      </button>
                    ))}
                  </div>
                </div>

                <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                    <input
                      className="pf-input"
                      style={{ flex: '1 1 320px' }}
                      value={incidentTitle}
                      onChange={e => setIncidentTitle(e.target.value)}
                      placeholder="Tiêu đề incident (mặc định sẽ tạo tự động)"
                    />
                    <select
                      className="pf-select"
                      value={incidentSeverity}
                      onChange={e => setIncidentSeverity(e.target.value)}
                    >
                      {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(level => (
                        <option key={level} value={level}>{level}</option>
                      ))}
                    </select>
                    <button className="gv-btn" onClick={createIncident} disabled={creatingIncident}>
                      {creatingIncident ? 'Đang tạo…' : 'Tạo Incident từ đường'
                      }
                    </button>
                  </div>

                  {createResult && (
                    <div className="incident-list-card" style={{ padding: 14, background: '#0f1624', border: '1px solid #2d3748' }}>
                      <div style={{ color: '#63b3ed', fontWeight: 700 }}>Incident đã tạo</div>
                      <div style={{ marginTop: 6, color: '#a0aec0' }}>ID: {createResult.id}</div>
                      <div style={{ color: '#a0aec0' }}>Title: {createResult.title}</div>
                      <div style={{ color: '#a0aec0' }}>Status: {createResult.status}</div>
                    </div>
                  )}
                  {createError && (
                    <div className="empty" style={{ color: '#fc8181', padding: '10px 0' }}>{createError}</div>
                  )}
                </div>

                <div style={{ marginTop: 12, color: '#a0aec0', fontSize: 13 }}>
                  <strong>Đường đã chọn:</strong> {result.paths[selectedPathIndex]?.join(' → ')}
                </div>
              </div>
            )}

            <GraphView data={result} />
          </div>
        ) : (
          <div className="card">
            <div className="empty">
              Không tìm thấy đường đi giữa hai entity trong {maxHops} hop.
            </div>
          </div>
        )
      )}
    </>
  )
}
