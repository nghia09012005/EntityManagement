import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getNeighbors } from '../api'
import EntityBadge from '../components/EntityBadge'
import GraphView from '../components/GraphView'

const LABEL_MAP = {
  user: 'User', host: 'Host', ip: 'IP',
  domain: 'Domain', filehash: 'FileHash',
}

const ID_PROP = {
  user: 'username', host: 'hostname', ip: 'address',
  domain: 'name', filehash: 'hash',
}

// Split properties into base info vs enrichment
const ENRICHMENT_KEYS = new Set(['country', 'asn', 'verdict', 'malicious', 'family'])

function PropRow({ k, v }) {
  let display = String(v ?? '—')
  let cls = 'prop-val'
  if (k === 'malicious') {
    display = v ? '⚠ Yes' : '✓ No'
    cls = v ? 'prop-val bad' : 'prop-val ok'
  }
  if (k === 'verdict' && v === 'MALICIOUS') cls = 'prop-val bad'
  if (k === 'verdict' && v === 'CLEAN')     cls = 'prop-val ok'
  return (
    <div className="prop-row">
      <span className="prop-key">{k}</span>
      <span className={cls}>{display}</span>
    </div>
  )
}

export default function EntityDetailPage() {
  const { type, value } = useParams()
  const navigate = useNavigate()
  const [graph, setGraph]     = useState(null)
  const [loading, setLoading] = useState(true)

  const label = LABEL_MAP[type] || type
  const decoded = decodeURIComponent(value)

  useEffect(() => {
    setLoading(true)
    getNeighbors(type, decoded, 1)
      .then(setGraph)
      .catch(() => setGraph(null))
      .finally(() => setLoading(false))
  }, [type, decoded])

  // Find source node from graph data
  const sourceNode = graph?.nodes?.find(n => {
    const idProp = ID_PROP[type]
    return n.properties[idProp] === decoded
  })
  const props = sourceNode?.properties || {}

  const baseProps = Object.entries(props).filter(([k]) => !ENRICHMENT_KEYS.has(k))
  const enrichProps = Object.entries(props).filter(([k]) => ENRICHMENT_KEYS.has(k))

  const neighbors = graph?.edges?.map(e => {
    const isOut = e.from === sourceNode?.id
    const neighborId = isOut ? e.to : e.from
    const neighborNode = graph.nodes.find(n => n.id === neighborId)
    return { edge: e, node: neighborNode, direction: isOut ? '→' : '←' }
  }) || []

  return (
    <>
      <div className="back-btn" onClick={() => navigate('/')}>
        ← Back to list
      </div>

      <div className="page-title">
        <EntityBadge label={label} />
        <span className="mono">{decoded}</span>
      </div>
      <div className="page-sub">Entity detail · 1-hop graph</div>

      {loading ? (
        <div className="loading">Đang tải…</div>
      ) : (
        <>
          <div className="grid-2">
            {/* Info */}
            <div className="card">
              <div className="card-header">
                <span className="card-title">Thông tin cơ bản</span>
              </div>
              <div className="prop-list">
                {baseProps.length
                  ? baseProps.map(([k, v]) => <PropRow key={k} k={k} v={v} />)
                  : <span className="empty" style={{padding:'8px 0'}}>Không có data</span>
                }
              </div>
            </div>

            {/* Enrichment */}
            <div className="card">
              <div className="card-header">
                <span className="card-title">Enrichment</span>
              </div>
              <div className="prop-list">
                {enrichProps.length
                  ? enrichProps.map(([k, v]) => <PropRow key={k} k={k} v={v} />)
                  : <div className="empty" style={{padding:'8px 0'}}>Chưa có enrichment data</div>
                }
              </div>
            </div>
          </div>

          {/* Relationships */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">Quan hệ</span>
              <span className="card-count">{neighbors.length} relationships</span>
            </div>
            {neighbors.length === 0 ? (
              <div className="empty">Không có quan hệ nào</div>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Direction</th>
                    <th>Relationship</th>
                    <th>Entity</th>
                    <th>First Seen</th>
                    <th>Last Seen</th>
                    <th>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {neighbors.map((item, i) => {
                    const np = item.node?.properties || {}
                    const nLabel = item.node?.label
                    const nVal = np.username || np.hostname || np.address || np.name || np.hash || '—'
                    const nType = nLabel?.toLowerCase()
                    return (
                      <tr key={i}>
                        <td>{item.direction}</td>
                        <td><span style={{color:'#b794f4', fontWeight:600}}>{item.edge.type}</span></td>
                        <td>
                          <EntityBadge label={nLabel} />{' '}
                          <span
                            className="link mono"
                            style={{marginLeft:8}}
                            onClick={() => navigate(`/entity/${nType}/${encodeURIComponent(nVal)}`)}
                          >
                            {nVal}
                          </span>
                        </td>
                        <td style={{color:'#718096', fontSize:13}}>{item.edge.properties?.firstSeen || '—'}</td>
                        <td style={{color:'#718096', fontSize:13}}>{item.edge.properties?.lastSeen || '—'}</td>
                        <td style={{fontWeight:600}}>{item.edge.properties?.count ?? '—'}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </div>

          {/* Graph */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">Graph Visualization</span>
              <span className="card-count">1-hop neighbors</span>
            </div>
            <GraphView data={graph} />
          </div>
        </>
      )}
    </>
  )
}
