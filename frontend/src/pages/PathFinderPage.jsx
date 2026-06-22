import { useState } from 'react'
import { findPath, ENTITY_TYPES, ENTITY_LABELS } from '../api'
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
