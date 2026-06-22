const BASE_URL = 'http://localhost:8080'

export const ENTITY_TYPES = ['user', 'host', 'ip', 'domain', 'filehash']

export const ENTITY_LABELS = {
  user:     'User',
  host:     'Host',
  ip:       'IP',
  domain:   'Domain',
  filehash: 'FileHash',
}

export const uploadLogFile = (file) => {
  const form = new FormData()
  form.append('file', file)
  return fetch(`${BASE_URL}/api/files/upload`, { method: 'POST', body: form }).then(r => {
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return r.json()
  })
}

export const listEntities = (type) =>
  fetch(`${BASE_URL}/api/graph/entities/${type}`).then(r => r.json())

export const getNeighbors = (label, value, hops = 1) =>
  fetch(`${BASE_URL}/api/graph/${label}/${encodeURIComponent(value)}/neighbors?hops=${hops}`)
    .then(r => r.json())

export const findPath = (fromType, fromValue, toType, toValue, maxHops = 6, mode = 'shortest') =>
  fetch(`${BASE_URL}/api/graph/path?fromType=${fromType}&fromValue=${encodeURIComponent(fromValue)}&toType=${toType}&toValue=${encodeURIComponent(toValue)}&maxHops=${maxHops}&mode=${mode}`)
    .then(r => r.json())
