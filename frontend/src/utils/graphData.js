export function dedupeByKey(items, getKey) {
  const seen = new Set()
  return (items || []).filter((item) => {
    const key = getKey(item)
    if (key == null || key === '') return true
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

export function normalizeGraphData(data) {
  if (!data) return { nodes: [], edges: [] }

  const nodes = []
  const nodeIds = new Set()
  ;(data.nodes || []).forEach((node) => {
    if (!node || node.id == null) return
    const id = String(node.id)
    if (nodeIds.has(id)) return
    nodeIds.add(id)
    nodes.push(node)
  })

  const edges = []
  const edgeIds = new Set()
  ;(data.edges || []).forEach((edge) => {
    if (!edge || edge.from == null || edge.to == null) return
    const id = `${String(edge.from)}|${String(edge.type || '')}|${String(edge.to)}`
    if (edgeIds.has(id)) return
    edgeIds.add(id)
    edges.push(edge)
  })

  return { nodes, edges }
}
