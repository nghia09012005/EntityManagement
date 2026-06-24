export const NODE_COLORS = {
  User:          { bg: '#276749', border: '#68d391', font: '#c6f6d5' },
  Host:          { bg: '#2a4365', border: '#63b3ed', font: '#bee3f8' },
  IP:            { bg: '#7b341e', border: '#f6ad55', font: '#feebc8' },
  Domain:        { bg: '#44337a', border: '#b794f4', font: '#e9d8fd' },
  FileHash:      { bg: '#742a2a', border: '#fc8181', font: '#fed7d7' },
  Url:           { bg: '#1a3a2e', border: '#48bb78', font: '#c6f6d5' },
  Process:       { bg: '#2d2050', border: '#b794f4', font: '#e9d8fd' },
  CloudResource: { bg: '#0e2d3a', border: '#76e4f7', font: '#c4f1f9' },
  Email:         { bg: '#3a2910', border: '#f6ad55', font: '#feebc8' },
  Cve:           { bg: '#3a1a20', border: '#fc8181', font: '#fed7d7' },
}

const BADGE_CLASS = {
  user:          'badge-user',
  host:          'badge-host',
  ip:            'badge-ip',
  domain:        'badge-domain',
  filehash:      'badge-filehash',
  url:           'badge-url',
  process:       'badge-process',
  cloudresource: 'badge-cloudresource',
  email:         'badge-email',
  cve:           'badge-cve',
}

export default function EntityBadge({ label }) {
  const cls = BADGE_CLASS[label?.toLowerCase()] || ''
  return <span className={`badge ${cls}`}>{label}</span>
}
