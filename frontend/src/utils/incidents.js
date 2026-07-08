const AFFECTED_ENTITY_KEY_ALIASES = {
  user: ['users', 'user'],
  host: ['hosts', 'host'],
  ip: ['ips', 'ip'],
  domain: ['domains', 'domain'],
  filehash: ['filehashes', 'filehash'],
  url: ['urls', 'url'],
  process: ['processes', 'process'],
  cloudresource: ['cloudresources', 'cloudresource'],
  email: ['emails', 'email'],
  cve: ['cves', 'cve'],
}

function getAffectedEntityValues(incident, entityType) {
  if (!incident) return []
  const affected = incident.affectedEntities || {}
  const keys = [entityType, entityType?.toLowerCase(), ...(AFFECTED_ENTITY_KEY_ALIASES[entityType] || [])]
    .filter(Boolean)
  const seen = new Set()

  for (const key of keys) {
    if (seen.has(key)) continue
    seen.add(key)
    const value = affected[key]
    if (value == null) continue
    return Array.isArray(value) ? value : [value]
  }

  return []
}

export function getUnresolvedIncidentEntityValues(incidents, entityType) {
  const values = new Set()

  ;(incidents || []).forEach((incident) => {
    if (!incident || String(incident.status || '').toUpperCase() === 'RESOLVED') return

    getAffectedEntityValues(incident, entityType).forEach((value) => {
      if (value == null) return
      const text = String(value).trim().toLowerCase()
      if (text) values.add(text)
    })
  })

  return values
}

export function findIncidentForEntity(incidents, entityType, entityValue) {
  if (!entityType || entityValue == null || entityValue === '') return null

  const normalizedValue = String(entityValue).trim().toLowerCase()
  if (!normalizedValue) return null

  return (incidents || []).find((incident) => {
    if (!incident || String(incident.status || '').toUpperCase() === 'RESOLVED') return false
    return getAffectedEntityValues(incident, entityType).some((value) => {
      return String(value || '').trim().toLowerCase() === normalizedValue
    })
  }) || null
}

export function isEntityInUnresolvedIncident(entityType, entityValue, incidents) {
  return Boolean(findIncidentForEntity(incidents, entityType, entityValue))
}
