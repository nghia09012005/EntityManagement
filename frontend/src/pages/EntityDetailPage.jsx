import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getNeighbors, fetchEnrichmentByEventId } from '../api'
import EntityBadge from '../components/EntityBadge'
import GraphView from '../components/GraphView'

const LABEL_MAP = {
  user: 'User', host: 'Host', ip: 'IP',
  domain: 'Domain', filehash: 'FileHash',
  url: 'Url', process: 'Process', cloudresource: 'CloudResource',
  email: 'Email', cve: 'Cve',
}

const ID_PROP = {
  user: 'username', host: 'hostname', ip: 'address',
  domain: 'name', filehash: 'hash',
  url: 'url', process: 'name', cloudresource: 'resourceId',
  email: 'address', cve: 'cveId',
}

/** Flatten MongoDB enrichment map into display key-value pairs. */
function flattenEnrichment(type, raw) {
  if (!raw || Object.keys(raw).length === 0) return []
  const result = {}
  if (type === 'ip') {
    const geo   = raw.geo   || raw.srcGeo   || raw.dstGeo   || {}
    const intel = raw.ipIntel || raw.srcIpIntel || raw.dstIpIntel || {}
    if (geo.country)     result.country     = geo.country
    if (geo.city)        result.city        = geo.city
    if (geo.asn)         result.asn         = geo.asn
    if (intel.abuseScore  != null) result.abuseScore  = intel.abuseScore
    if (intel.threatLevel != null) result.threatLevel = intel.threatLevel
    if (intel.malicious   != null) result.isMalicious = intel.malicious
  } else if (type === 'filehash') {
    const mal = raw.malware || {}
    if (mal.verdict   != null) result.verdict   = mal.verdict
    if (mal.malicious != null) result.malicious = mal.malicious
    if (mal.family)            result.family    = mal.family
  }
  return Object.entries(result)
}

const THREAT_COLORS = {
  CRITICAL: { bg: '#4a1a1a', color: '#fc8181', border: '#742a2a' },
  HIGH:     { bg: '#3d2100', color: '#f6ad55', border: '#7b341e' },
  MEDIUM:   { bg: '#3d3400', color: '#f6e05e', border: '#744210' },
  LOW:      { bg: '#1a3a1a', color: '#68d391', border: '#276749' },
  NONE:     { bg: '#1a1d2e', color: '#718096', border: '#2d3748' },
}

const SEVERITY_COLORS = {
  CRITICAL: '#fc8181', HIGH: '#f6ad55', MEDIUM: '#f6e05e', LOW: '#68d391',
}

function ThreatBadge({ level }) {
  const c = THREAT_COLORS[level] || THREAT_COLORS.NONE
  return (
    <span style={{
      padding: '1px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700,
      background: c.bg, color: c.color, border: `1px solid ${c.border}`,
    }}>{level || '—'}</span>
  )
}

function SeverityBadge({ severity }) {
  const color = SEVERITY_COLORS[severity] || '#718096'
  return (
    <span style={{
      padding: '1px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700,
      background: '#1a1d2e', color, border: `1px solid ${color}44`,
    }}>{severity || '—'}</span>
  )
}

function PropRow({ k, v }) {
  if (k === 'threatLevel') return (
    <div className="prop-row">
      <span className="prop-key">{k}</span>
      <ThreatBadge level={v} />
    </div>
  )
  if (k === 'isMalicious' || k === 'malicious') return (
    <div className="prop-row">
      <span className="prop-key">{k}</span>
      <span className={`prop-val ${v ? 'bad' : 'ok'}`}>{v ? '⚠ Yes' : '✓ No'}</span>
    </div>
  )
  if (k === 'abuseScore') {
    const cls = v >= 75 ? 'bad' : v >= 25 ? 'prop-val' : 'ok'
    return (
      <div className="prop-row">
        <span className="prop-key">{k}</span>
        <span className={`prop-val ${cls}`}>{v}/100</span>
      </div>
    )
  }
  if (k === 'verdict') {
    const cls = v === 'MALICIOUS' ? 'bad' : v === 'CLEAN' ? 'ok' : ''
    return (
      <div className="prop-row">
        <span className="prop-key">{k}</span>
        <span className={`prop-val ${cls}`}>{String(v ?? '—')}</span>
      </div>
    )
  }
  return (
    <div className="prop-row">
      <span className="prop-key">{k}</span>
      <span className="prop-val">{String(v ?? '—')}</span>
    </div>
  )
}

/** Extra details cell based on relationship type */
function EdgeDetails({ edge }) {
  const p = edge.properties || {}
  if (edge.type === 'SAME_AS') return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span style={{
        padding: '1px 7px', borderRadius: 4, fontSize: 11, fontWeight: 700,
        background: '#2a1f4a', color: '#d2b8ff', border: '1px solid #553c9a',
      }}>DEDUP</span>
      <span style={{ color: '#b794f4', fontSize: 12 }}>{p.reason}</span>
      <span style={{ color: '#718096', fontSize: 11 }}>{p.confidence != null ? `(${(p.confidence * 100).toFixed(0)}%)` : ''}</span>
    </span>
  )
  if (edge.type === 'ALERTED_FROM' || edge.type === 'TARGETED_AT') return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <SeverityBadge severity={p.severity} />
      <span style={{ color: '#a0aec0', fontSize: 12 }}>{p.alertName || ''}</span>
    </span>
  )
  if (edge.type === 'EXECUTED_ON' && p.processName) return (
    <span className="mono" style={{ color: '#63b3ed', fontSize: 12 }}>{p.processName}</span>
  )
  if (edge.type === 'CONNECTED_TO' && p.dstPort != null) return (
    <span style={{ color: '#68d391', fontSize: 12 }}>port {p.dstPort}</span>
  )
  return <span style={{ color: '#4a5568' }}>—</span>
}

export default function EntityDetailPage() {
  const { type, value } = useParams()
  const navigate = useNavigate()
  const [graph, setGraph]         = useState(null)
  const [loading, setLoading]     = useState(true)
  const [hops, setHops]           = useState(1)
  const [enrichment, setEnrichment] = useState({})

  const label   = LABEL_MAP[type] || type
  const decoded = decodeURIComponent(value)

  useEffect(() => {
    setLoading(true)
    getNeighbors(type, decoded, hops)
      .then(setGraph)
      .catch(() => setGraph(null))
      .finally(() => setLoading(false))
  }, [type, decoded, hops])

  useEffect(() => {
    if (type !== 'ip' && type !== 'filehash') { setEnrichment({}); return }
    if (!graph?.edges?.length) return
    const eventId = graph.edges
      .map(e => e.properties?.lastEventId)
      .find(id => !!id)
    if (!eventId) return
    fetchEnrichmentByEventId(eventId).then(setEnrichment).catch(() => setEnrichment({}))
  }, [type, graph])

  const sourceNode = graph?.nodes?.find(n => n.properties[ID_PROP[type]] === decoded)
  const props = sourceNode?.properties || {}

  const baseProps   = Object.entries(props)
  const enrichProps = flattenEnrichment(type, enrichment)

  const neighbors = graph?.edges?.map(e => {
    const isOut      = e.from === sourceNode?.id
    const neighborId = isOut ? e.to : e.from
    const neighborNode = graph.nodes.find(n => n.id === neighborId)
    return { edge: e, node: neighborNode, direction: isOut ? '→' : '←' }
  }) || []

  const sameAsLinks   = neighbors.filter(n => n.edge.type === 'SAME_AS')
  const normalLinks   = neighbors.filter(n => n.edge.type !== 'SAME_AS')

  return (
    <>
      <div className="back-btn" onClick={() => navigate('/')}>← Back to list</div>

      <div className="page-title">
        <EntityBadge label={label} />
        <span className="mono">{decoded}</span>
      </div>

      <div className="page-sub" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span>Entity detail</span>
        <span style={{ color: '#2d3748' }}>·</span>
        <span style={{ fontSize: 12, color: '#718096' }}>Hops</span>
        {[1, 2, 3, 5].map(h => (
          <button key={h} onClick={() => setHops(h)} style={{
            padding: '2px 10px', borderRadius: 5, fontSize: 12, cursor: 'pointer',
            border: '1px solid',
            borderColor: hops === h ? '#63b3ed' : '#2d3748',
            background:  hops === h ? '#2b4a7a' : '#1a1d2e',
            color:       hops === h ? '#bee3f8' : '#718096',
          }}>{h}-hop</button>
        ))}
        {loading && <span style={{ fontSize: 12, color: '#63b3ed' }}>Loading…</span>}
      </div>

      {loading ? <div className="loading">Đang tải…</div> : (
        <>
          <div className="grid-2">
            {/* Base info */}
            <div className="card">
              <div className="card-header"><span className="card-title">Thông tin cơ bản</span></div>
              <div className="prop-list">
                {baseProps.length
                  ? baseProps.map(([k, v]) => <PropRow key={k} k={k} v={v} />)
                  : <span className="empty" style={{ padding: '8px 0' }}>Không có data</span>}
              </div>
            </div>

            {/* Enrichment */}
            <div className="card">
              <div className="card-header"><span className="card-title">Enrichment</span></div>
              <div className="prop-list">
                {enrichProps.length
                  ? enrichProps.map(([k, v]) => <PropRow key={k} k={k} v={v} />)
                  : <div className="empty" style={{ padding: '8px 0' }}>Chưa có enrichment data</div>}
              </div>
            </div>
          </div>

          {/* SAME_AS dedup links */}
          {sameAsLinks.length > 0 && (
            <div className="card" style={{ borderColor: '#553c9a' }}>
              <div className="card-header" style={{ background: '#1a1230' }}>
                <span className="card-title" style={{ color: '#d2b8ff' }}>Entity Aliases (SAME_AS)</span>
                <span className="card-count">{sameAsLinks.length} links</span>
              </div>
              <table>
                <thead>
                  <tr>
                    <th>Dir</th><th>Entity</th><th>Rule</th><th>Confidence</th><th>Detected</th>
                  </tr>
                </thead>
                <tbody>
                  {sameAsLinks.map((item, i) => {
                    const np   = item.node?.properties || {}
                    const nLabel = item.node?.label
                    const nVal = np.username || np.hostname || np.address || np.name || np.hash || np.url || np.resourceId || np.cveId || '—'
                    const nType = nLabel?.toLowerCase()
                    const p = item.edge.properties || {}
                    return (
                      <tr key={i} style={{ background: '#130d1f' }}>
                        <td style={{ color: '#b794f4' }}>{item.direction}</td>
                        <td>
                          <EntityBadge label={nLabel} />{' '}
                          <span className="link mono" style={{ marginLeft: 8 }}
                            onClick={() => navigate(`/entity/${nType}/${encodeURIComponent(nVal)}`)}>
                            {nVal}
                          </span>
                        </td>
                        <td style={{ color: '#b794f4', fontSize: 12 }}>{p.reason || '—'}</td>
                        <td style={{ color: '#a0aec0', fontSize: 12 }}>
                          {p.confidence != null ? `${(p.confidence * 100).toFixed(0)}%` : '—'}
                        </td>
                        <td style={{ color: '#718096', fontSize: 12 }}>{p.detectedAt || '—'}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Relationships */}
          <div className="card">
            <div className="card-header">
              <span className="card-title">Quan hệ</span>
              <span className="card-count">{normalLinks.length} relationships</span>
            </div>
            {normalLinks.length === 0 ? (
              <div className="empty">Không có quan hệ nào</div>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Dir</th><th>Relationship</th><th>Entity</th>
                    <th>Details</th><th>First Seen</th><th>Last Seen</th><th>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {normalLinks.map((item, i) => {
                    const np    = item.node?.properties || {}
                    const nLabel = item.node?.label
                    const nVal  = np.username || np.hostname || np.address || np.name || np.hash || np.url || np.resourceId || np.cveId || '—'
                    const nType = nLabel?.toLowerCase()
                    return (
                      <tr key={i}>
                        <td>{item.direction}</td>
                        <td><span style={{ color: '#b794f4', fontWeight: 600 }}>{item.edge.type}</span></td>
                        <td>
                          <EntityBadge label={nLabel} />{' '}
                          <span className="link mono" style={{ marginLeft: 8 }}
                            onClick={() => navigate(`/entity/${nType}/${encodeURIComponent(nVal)}`)}>
                            {nVal}
                          </span>
                        </td>
                        <td><EdgeDetails edge={item.edge} /></td>
                        <td style={{ color: '#718096', fontSize: 13 }}>{item.edge.properties?.firstSeen || '—'}</td>
                        <td style={{ color: '#718096', fontSize: 13 }}>{item.edge.properties?.lastSeen || '—'}</td>
                        <td style={{ fontWeight: 600 }}>{item.edge.properties?.count ?? '—'}</td>
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
              <span className="card-count">
                {graph?.nodes?.length ?? 0} nodes · {graph?.edges?.length ?? 0} edges
              </span>
            </div>
            <GraphView data={graph} />
          </div>
        </>
      )}
    </>
  )
}
