export const NODE_COLORS = {
  User:     { bg: '#276749', border: '#68d391', font: '#c6f6d5' },
  Host:     { bg: '#2a4365', border: '#63b3ed', font: '#bee3f8' },
  IP:       { bg: '#7b341e', border: '#f6ad55', font: '#feebc8' },
  Domain:   { bg: '#44337a', border: '#b794f4', font: '#e9d8fd' },
  FileHash: { bg: '#742a2a', border: '#fc8181', font: '#fed7d7' },
}

export default function EntityBadge({ label }) {
  const key = label?.toLowerCase()
  const cls =
    key === 'user'     ? 'badge-user'     :
    key === 'host'     ? 'badge-host'     :
    key === 'ip'       ? 'badge-ip'       :
    key === 'domain'   ? 'badge-domain'   :
    key === 'filehash' ? 'badge-filehash' : ''
  return <span className={`badge ${cls}`}>{label}</span>
}
