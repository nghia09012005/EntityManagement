import { useEffect, useRef } from 'react'
import { Network } from 'vis-network'
import { DataSet } from 'vis-data'
import { NODE_COLORS } from './EntityBadge'

function getNodeLabel(node) {
  const p = node.properties
  return (
    p.username || p.hostname || p.address ||
    p.name || (p.hash ? p.hash.slice(0, 12) + '…' : node.id)
  )
}

function getRelLabel(edge) {
  const count = edge.properties?.count
  return count ? `${edge.type}\n×${count}` : edge.type
}

const colorFor = (label) => {
  const c = NODE_COLORS[label] || { bg: '#2d3748', border: '#718096', font: '#a0aec0' }
  return {
    background: c.bg,
    border: c.border,
    highlight: { background: c.bg, border: c.font },
  }
}

export default function GraphView({ data }) {
  const ref = useRef(null)
  const hasData = (data?.nodes?.length ?? 0) > 0

  useEffect(() => {
    if (!ref.current || !hasData) return

    const nodes = new DataSet(
      data.nodes.map(n => ({
        id: n.id,
        label: getNodeLabel(n),
        title: `${n.label}: ${Object.entries(n.properties).map(([k,v]) => `${k}=${v}`).join(', ')}`,
        color: colorFor(n.label),
        font: { color: '#e2e8f0', size: 13 },
        shape: 'box',
        borderWidth: 2,
        margin: 8,
      }))
    )

    const edges = new DataSet(
      data.edges.map((e, i) => ({
        id: i,
        from: e.from,
        to: e.to,
        label: getRelLabel(e),
        arrows: 'to',
        color: { color: '#4a5568', highlight: '#63b3ed' },
        font: { color: '#a0aec0', size: 11, align: 'middle' },
        smooth: { type: 'curvedCW', roundness: 0.15 },
      }))
    )

    const network = new Network(
      ref.current,
      { nodes, edges },
      {
        layout: { improvedLayout: true },
        physics: {
          solver: 'forceAtlas2Based',
          forceAtlas2Based: { gravitationalConstant: -60, springLength: 120 },
          stabilization: { iterations: 150 },
        },
        interaction: { hover: true, tooltipDelay: 100 },
        edges: { width: 1.5 },
      }
    )

    return () => network.destroy()
  }, [data])  // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <>
      {!hasData && <div className="empty">Không có quan hệ nào</div>}
      {/* Div này luôn ở trong DOM để ref luôn được set trước khi useEffect chạy */}
      <div
        ref={ref}
        style={{
          width: '100%',
          height: '440px',
          background: '#11131f',
          display: hasData ? 'block' : 'none',
        }}
      />
    </>
  )
}
