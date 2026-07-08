import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Network } from 'vis-network'
import { DataSet } from 'vis-data'
import { NODE_COLORS } from './EntityBadge'
import { getNeighbors } from '../api'
import { normalizeGraphData } from '../utils/graphData'

/* ──────── helpers ──────── */
const LABEL_TO_TYPE = {
  User: 'user', Host: 'host', IP: 'ip', Domain: 'domain', FileHash: 'filehash',
  Url: 'url', Process: 'process', CloudResource: 'cloudresource', Email: 'email', Cve: 'cve',
}
const ID_PROP = {
  User: 'username', Host: 'hostname', IP: 'address', Domain: 'name', FileHash: 'hash',
  Url: 'url', Process: 'name', CloudResource: 'resourceId', Email: 'address', Cve: 'cveId',
}
const NODE_GROUP = {
  User: 'User', IP: 'IP', Host: 'Device', CloudResource: 'Device', FileHash: 'File',
  Url: 'URL', Domain: 'Domain', Process: 'Process', Email: 'Email', Cve: 'CVE',
}

function colorFor(label) {
  const c = NODE_COLORS[label] || { bg: '#2d3748', border: '#718096', font: '#a0aec0' }
  return { background: c.bg, border: c.border, highlight: { background: c.bg, border: c.font } }
}

function toVisNode(n) {
  const label  = n.label
  const idProp = ID_PROP[label]
  const val    = n.properties?.[idProp] || n.id
  return {
    id: n.id,
    label: val.length > 20 ? val.slice(0, 18) + '…' : val,
    title: `<b>${label}</b><br>` +
           Object.entries(n.properties || {}).map(([k, v]) => `${k}: ${v}`).join('<br>'),
    color: colorFor(label),
    font: { color: '#e2e8f0', size: 13 },
    shape: 'box', borderWidth: 2, margin: 8,
    // internal meta (not used by vis)
    _label: label, _props: n.properties || {}, _val: val,
  }
}

function toVisEdge(e) {
  const eid   = `${e.from}|${e.type}|${e.to}`
  const count = e.properties?.count
  return {
    id: eid, from: e.from, to: e.to,
    label: count ? `${e.type}\n×${count}` : e.type,
    arrows: 'to',
    color: { color: '#4a5568', highlight: '#63b3ed' },
    font: { color: '#a0aec0', size: 11, align: 'middle' },
    smooth: { type: 'curvedCW', roundness: 0.15 },
    // meta
    _type: e.type,
    _firstSeen: e.properties?.firstSeen || '',
    _lastSeen:  e.properties?.lastSeen  || '',
  }
}

function networkOptions(layout) {
  const base = {
    interaction: { hover: true, tooltipDelay: 80, navigationButtons: true, keyboard: true },
    edges: { width: 1.5 },
  }
  if (layout === 'hierarchical') {
    return { ...base,
      layout: { hierarchical: { enabled: true, direction: 'UD', sortMethod: 'directed',
                                levelSeparation: 110, nodeSpacing: 130 } },
      physics: { enabled: false },
    }
  }
  return { ...base,
    layout: { improvedLayout: true },
    physics: {
      solver: 'forceAtlas2Based',
      forceAtlas2Based: { gravitationalConstant: -60, springLength: 130 },
      stabilization: { iterations: 150 },
    },
  }
}

/* ──────── component ──────── */
export default function GraphView({ data }) {
  const containerRef  = useRef(null)
  const networkRef    = useRef(null)
  const nodesDS       = useRef(null)
  const edgesDS       = useRef(null)
  const expandedRef   = useRef(new Set())    // node IDs already expanded

  const [layout,        setLayout]        = useState('force')
  const [allRelTypes,   setAllRelTypes]   = useState([])
  const [hiddenRels,    setHiddenRels]    = useState(new Set())
  const [allNodeGroups, setAllNodeGroups] = useState([])
  const [hiddenNodeGroups, setHiddenNodeGroups] = useState(new Set())
  const [timeSteps,     setTimeSteps]     = useState([])
  const [timeIndex,     setTimeIndex]     = useState(-1)
  const [status,        setStatus]        = useState('')
  const [selected,      setSelected]      = useState(null)   // { label, val, type }

  const navigate = useNavigate()
  const hasData  = (data?.nodes?.length ?? 0) > 0

  /* ── init / reinit when data prop changes ── */
  useEffect(() => {
    if (!containerRef.current || !data) return

    const normalized = normalizeGraphData(data)
    nodesDS.current = new DataSet(normalized.nodes.map(toVisNode))
    edgesDS.current = new DataSet(normalized.edges.map(toVisEdge))
    expandedRef.current.clear()

    const types = [...new Set(normalized.edges.map(e => e.type))]
    const groups = [...new Set(normalized.nodes.map(n => NODE_GROUP[n.label] || n.label))]
    const steps = [...new Set(normalized.edges
      .map(e => e.properties?.firstSeen || e._firstSeen || '')
      .map(ts => ts && ts.slice(0, 10))
      .filter(Boolean))].sort()

    setAllRelTypes(types)
    setHiddenRels(new Set())
    setAllNodeGroups(groups)
    setHiddenNodeGroups(new Set())
    setTimeSteps(steps)
    setTimeIndex(steps.length > 0 ? steps.length - 1 : -1)
    setSelected(null)
    setStatus('Double-click node to expand  ·  Right-click node to open detail')

    const net = new Network(
      containerRef.current,
      { nodes: nodesDS.current, edges: edgesDS.current },
      networkOptions('force')
    )
    networkRef.current = net
    setLayout('force')

    /* expand on double-click */
    net.on('doubleClick', async ({ nodes: ids }) => {
      if (!ids.length) return
      const nodeId = ids[0]
      if (expandedRef.current.has(nodeId)) {
        setStatus(`${nodeId} already expanded`)
        return
      }
      const vn = nodesDS.current.get(nodeId)
      if (!vn) return
      const type = LABEL_TO_TYPE[vn._label]
      const val  = vn._props[ID_PROP[vn._label]]
      if (!type || !val) return

      setStatus(`Expanding ${vn._label} "${val}"…`)
      try {
        const result = await getNeighbors(type, encodeURIComponent(val), 1)
        let addedNodes = 0, addedEdges = 0
        ;(result.nodes || []).forEach(n => {
          if (!nodesDS.current.get(n.id)) { nodesDS.current.add(toVisNode(n)); addedNodes++ }
        })
        ;(result.edges || []).forEach(e => {
          const eid = `${e.from}|${e.type}|${e.to}`
          if (!edgesDS.current.get(eid)) { edgesDS.current.add(toVisEdge(e)); addedEdges++ }
        })
        // add new rel types to filter list
        const newTypes = (result.edges || []).map(e => e.type)
        setAllRelTypes(prev => [...new Set([...prev, ...newTypes])])
        expandedRef.current.add(nodeId)
        // mark expanded node with dashed border
        nodesDS.current.update({ id: nodeId, borderWidth: 3, shapeProperties: { borderDashes: [6, 3] } })
        setStatus(`Expanded "${val}" — +${addedNodes} nodes, +${addedEdges} edges`)
      } catch (err) {
        setStatus(`Expand failed: ${err.message}`)
      }
    })

    /* navigate on right-click */
    net.on('oncontext', ({ nodes: ids, event }) => {
      event?.preventDefault?.()
      if (!ids.length) return
      const vn = nodesDS.current.get(ids[0])
      if (!vn) return
      const type = LABEL_TO_TYPE[vn._label]
      const val  = vn._val
      if (type && val) navigate(`/entity/${type}/${encodeURIComponent(val)}`)
    })

    /* track selection for toolbar */
    net.on('selectNode', ({ nodes: ids }) => {
      const vn = nodesDS.current.get(ids[0])
      if (!vn) return
      const type = LABEL_TO_TYPE[vn._label]
      setSelected({ label: vn._label, val: vn._val, type })
    })
    net.on('deselectNode', () => setSelected(null))

    return () => { net.destroy(); networkRef.current = null }
  }, [data])  // eslint-disable-line react-hooks/exhaustive-deps

  /* ── re-apply filters when they change ── */
  useEffect(() => {
    if (!edgesDS.current || !nodesDS.current) return

    const visibleNodes = new Set()
    nodesDS.current.get().forEach(node => {
      const group = NODE_GROUP[node._label] || node._label
      const hidden = hiddenNodeGroups.has(group)
      nodesDS.current.update({ id: node.id, hidden })
      if (!hidden) visibleNodes.add(node.id)
    })

    edgesDS.current.get().forEach(edge => {
      let hidden = hiddenRels.has(edge._type)
      if (!hidden && timeIndex >= 0 && edge._firstSeen) {
        const selectedDate = timeSteps[timeIndex]
        hidden = edge._firstSeen.slice(0, 10) > selectedDate
      }
      if (!hidden && (!visibleNodes.has(edge.from) || !visibleNodes.has(edge.to))) hidden = true
      edgesDS.current.update({ id: edge.id, hidden })
    })

    nodesDS.current.get().forEach(node => {
      if (node.hidden) return
      const connected = edgesDS.current.get({ filter: edge => !edge.hidden && (edge.from === node.id || edge.to === node.id) })
      if (connected.length === 0) {
        nodesDS.current.update({ id: node.id, hidden: true })
      }
    })
  }, [hiddenRels, hiddenNodeGroups, timeIndex, timeSteps])

  /* ── layout switch ── */
  const switchLayout = (l) => {
    setLayout(l)
    if (!networkRef.current) return
    networkRef.current.setOptions(networkOptions(l))
    if (l === 'force') networkRef.current.stabilize(100)
  }

  /* ── rel type toggle ── */
  const toggleRel = (type) => {
    setHiddenRels(prev => {
      const next = new Set(prev)
      next.has(type) ? next.delete(type) : next.add(type)
      return next
    })
  }

  const toggleNodeGroup = (group) => {
    setHiddenNodeGroups(prev => {
      const next = new Set(prev)
      next.has(group) ? next.delete(group) : next.add(group)
      return next
    })
  }

  const clearTimeFilter = () => {
    setTimeIndex(timeSteps.length > 0 ? timeSteps.length - 1 : -1)
  }

  const exportPng = () => {
    const canvas = containerRef.current?.querySelector('canvas')
    if (!canvas) return
    const a = document.createElement('a')
    a.download = 'graph.png'
    a.href = canvas.toDataURL('image/png')
    a.click()
  }

  const exportJson = () => {
    if (!nodesDS.current || !edgesDS.current) return
    const payload = {
      nodes: nodesDS.current.get().map(({ id, _label, _props, _val }) => ({ id, label: _label, value: _val, properties: _props })),
      edges: edgesDS.current.get().map(({ id, from, to, _type, _firstSeen, _lastSeen }) => ({ id, from, to, type: _type, firstSeen: _firstSeen, lastSeen: _lastSeen })),
    }
    const a = document.createElement('a')
    a.download = 'subgraph.json'
    a.href = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }))
    a.click()
  }

  return (
    <div className="gv-root">
      {/* ── Toolbar ── */}
      <div className="gv-toolbar">
        {/* Layout */}
        <div className="gv-group">
          <span className="gv-label">Layout</span>
          <button className={`gv-btn ${layout === 'force' ? 'active' : ''}`}
                  onClick={() => switchLayout('force')}>Force</button>
          <button className={`gv-btn ${layout === 'hierarchical' ? 'active' : ''}`}
                  onClick={() => switchLayout('hierarchical')}>Hierarchical</button>
        </div>

        {/* Node group filter */}
        {allNodeGroups.length > 0 && (
          <div className="gv-group gv-node-filters">
            <span className="gv-label">Nodes</span>
            {allNodeGroups.map(g => (
              <label key={g} className={`gv-chip ${hiddenNodeGroups.has(g) ? 'off' : ''}`}>
                <input type="checkbox" checked={!hiddenNodeGroups.has(g)} onChange={() => toggleNodeGroup(g)} />
                {g}
              </label>
            ))}
          </div>
        )}

        {/* Relationship filter */}
        {allRelTypes.length > 0 && (
          <div className="gv-group gv-rels">
            <span className="gv-label">Relations</span>
            {allRelTypes.map(t => (
              <label key={t} className={`gv-chip ${hiddenRels.has(t) ? 'off' : ''}`}>
                <input type="checkbox" checked={!hiddenRels.has(t)} onChange={() => toggleRel(t)} />
                {t}
              </label>
            ))}
          </div>
        )}

        {/* Time slider */}
        {timeSteps.length > 0 && (
          <div className="gv-group gv-time-slider">
            <span className="gv-label">Time</span>
            <input
              className="gv-slider"
              type="range"
              min={0}
              max={timeSteps.length - 1}
              step={1}
              value={timeIndex}
              onChange={(e) => setTimeIndex(Number(e.target.value))}
            />
            <span className="gv-time-label">{timeSteps[timeIndex] || 'All'}</span>
            <button className="gv-btn" onClick={clearTimeFilter}>All</button>
          </div>
        )}

        {/* Right-side actions */}
        <div className="gv-group" style={{ marginLeft: 'auto', gap: 6 }}>
          {selected && (
            <button className="gv-btn accent"
                    onClick={() => navigate(`/entity/${selected.type}/${encodeURIComponent(selected.val)}`)}>
              Open {selected.label}
            </button>
          )}
          <button className="gv-btn" onClick={() => networkRef.current?.fit({ animation: true })}>Fit</button>
          <button className="gv-btn" onClick={exportPng}>↓ PNG</button>
          <button className="gv-btn" onClick={exportJson}>↓ JSON</button>
        </div>
      </div>

      {/* Status bar */}
      {status && <div className="gv-status">{status}</div>}

      {/* Legend */}
      <div className="gv-legend">
        {Object.entries(NODE_COLORS).map(([lbl, c]) => (
          <span key={lbl} className="gv-legend-item"
                style={{ background: c.bg, borderColor: c.border, color: c.font }}>
            {lbl}
          </span>
        ))}
        <span className="gv-hint">dbl-click = expand  ·  right-click = open detail</span>
      </div>

      {/* Canvas */}
      {!hasData && <div className="empty">Không có quan hệ nào</div>}
      <div ref={containerRef} className="gv-canvas" style={{ display: hasData ? 'block' : 'none' }} />
    </div>
  )
}
