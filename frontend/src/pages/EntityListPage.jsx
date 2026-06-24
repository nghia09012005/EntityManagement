import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ENTITY_TYPES, ENTITY_LABELS, listEntities } from '../api'
import EntityBadge from '../components/EntityBadge'

const ID_PROP = {
  user:          'username',
  host:          'hostname',
  ip:            'address',
  domain:        'name',
  filehash:      'hash',
  url:           'url',
  process:       'name',
  cloudresource: 'resourceId',
  email:         'address',
  cve:           'cveId',
}

const COLUMNS = {
  user:          ['username'],
  host:          ['hostname'],
  ip:            ['address'],
  domain:        ['name'],
  filehash:      ['hash'],
  url:           ['url'],
  process:       ['name', 'path'],
  cloudresource: ['resourceId'],
  email:         ['address'],
  cve:           ['cveId'],
}

export default function EntityListPage() {
  const [active, setActive] = useState('user')
  const [data, setData]     = useState([])
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    setLoading(true)
    setData([])
    listEntities(active)
      .then(setData)
      .catch(() => setData([]))
      .finally(() => setLoading(false))
  }, [active])

  const cols = COLUMNS[active] || []
  const idProp = ID_PROP[active]

  return (
    <>
      <div className="tabs">
        {ENTITY_TYPES.map(t => (
          <div
            key={t}
            className={`tab ${active === t ? 'active' : ''}`}
            onClick={() => setActive(t)}
          >
            {ENTITY_LABELS[t]}
          </div>
        ))}
      </div>

      <div className="card">
        <div className="card-header">
          <EntityBadge label={ENTITY_LABELS[active]} />
          <span className="card-title">Entity List</span>
          <span className="card-count">{data.length} entities</span>
        </div>

        {loading ? (
          <div className="loading">Đang tải…</div>
        ) : data.length === 0 ? (
          <div className="empty">Không có dữ liệu — hãy upload log file trước.</div>
        ) : (
          <table>
            <thead>
              <tr>
                {cols.map(c => <th key={c}>{c}</th>)}
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.map((node) => {
                const p = node.properties
                const idVal = p[idProp]
                return (
                  <tr key={node.id}>
                    {cols.map(c => (
                      <td key={c}>
                        {c === 'hash'
                          ? <span className="mono">{p[c]}</span>
                          : String(p[c] ?? '—')
                        }
                      </td>
                    ))}
                    <td>
                      <span
                        className="link"
                        onClick={() => navigate(`/entity/${active}/${encodeURIComponent(idVal)}`)}
                      >
                        View detail →
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
