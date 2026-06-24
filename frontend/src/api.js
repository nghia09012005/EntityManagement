const BASE_URL = 'http://localhost:8080'

export const ENTITY_TYPES = ['user', 'host', 'ip', 'domain', 'filehash', 'url', 'process', 'cloudresource', 'email', 'cve']

export const ENTITY_LABELS = {
  user:          'User',
  host:          'Host',
  ip:            'IP',
  domain:        'Domain',
  filehash:      'FileHash',
  url:           'Url',
  process:       'Process',
  cloudresource: 'CloudResource',
  email:         'Email',
  cve:           'Cve',
}

export const ingestLog = (rawText) =>
  fetch(`${BASE_URL}/api/v1/ingest`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body: rawText,
  }).then(r => {
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return r.text()
  })

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

export const fetchDlqEvents = (page = 0, size = 20) =>
  fetch(`${BASE_URL}/api/dlq/events?page=${page}&size=${size}`).then(r => r.json())

export const fetchDlqSummary = () =>
  fetch(`${BASE_URL}/api/dlq/summary`).then(r => r.json())

export const fetchEnrichmentByEventId = (eventId) =>
  fetch(`${BASE_URL}/api/enrichment/event/${encodeURIComponent(eventId)}`)
    .then(r => r.json())
    .catch(() => ({}))
