const BASE_URL = 'http://localhost:8080'

const authHeaders = () => {
  const token = localStorage.getItem('soc_token') // Phải khớp với key ở LoginPage
  return token ? { Authorization: `Bearer ${token}` } : {}
}

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

export const loginUser = (username, password) =>
  fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  }).then(async r => {
    const data = await r.json()
    if (!r.ok) throw new Error(data.error || `HTTP ${r.status}`)
    return data
  })

export const registerUser = (username, email, password) =>
  fetch(`${BASE_URL}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, email, password }),
  }).then(async r => {
    const data = await r.json()
    if (!r.ok) throw new Error(data.error || `HTTP ${r.status}`)
    return data
  })

export const ingestLog = (rawText) =>
  fetch(`${BASE_URL}/api/v1/ingest`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain', ...authHeaders() },
    body: rawText,
  }).then(r => {
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return r.text()
  })

export const uploadLogFile = (file) => {
  const form = new FormData()
  form.append('file', file)
  return fetch(`${BASE_URL}/api/files/upload`, {
    method: 'POST',
    headers: authHeaders(),
    body: form,
  }).then(r => {
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return r.json()
  })
}

export const listEntities = (type) =>
  fetch(`${BASE_URL}/api/graph/entities/${type}`, { headers: authHeaders() }).then(r => r.json())

export const getNeighbors = (label, value, hops = 1) =>
  fetch(`${BASE_URL}/api/graph/${label}/${encodeURIComponent(value)}/neighbors?hops=${hops}`,
    { headers: authHeaders() }).then(r => r.json())

export const findPath = (fromType, fromValue, toType, toValue, maxHops = 6, mode = 'shortest') =>
  fetch(`${BASE_URL}/api/graph/path?fromType=${fromType}&fromValue=${encodeURIComponent(fromValue)}&toType=${toType}&toValue=${encodeURIComponent(toValue)}&maxHops=${maxHops}&mode=${mode}`,
    { headers: authHeaders() }).then(r => r.json())

export const fetchDlqEvents = (page = 0, size = 20) =>
  fetch(`${BASE_URL}/api/dlq/events?page=${page}&size=${size}`, { headers: authHeaders() }).then(r => r.json())

export const fetchDlqSummary = () =>
  fetch(`${BASE_URL}/api/dlq/summary`, { headers: authHeaders() }).then(r => r.json())

export const fetchEnrichmentByEventId = (eventId) =>
  fetch(`${BASE_URL}/api/enrichment/event/${encodeURIComponent(eventId)}`, { headers: authHeaders() })
    .then(r => r.json())
    .catch(() => ({}))
