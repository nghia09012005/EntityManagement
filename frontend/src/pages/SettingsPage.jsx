import { useEffect, useMemo, useState } from 'react'
import {
  createCustomEventType,
  createFieldMapping,
  deleteFieldMapping,
  fetchCustomEventTypes,
  fetchFieldMappingOptions,
  fetchFieldMappings,
  fetchUnknownFieldStats,
  updateFieldMapping,
} from '../api'

const DEFAULT_FORM = {
  eventType: 'AUTHENTICATION',
  sourceField: '',
  entityType: 'User',
  relationshipType: '',
  relatedEntityType: 'Host',
  relatedEventField: '',
  relationshipDirection: 'FROM_RELATED',
  enabled: true,
}

const EMPTY_CUSTOM_FIELD = { name: '', valueType: 'string', entityType: 'User', relationshipType: '', relatedEntityType: 'Host', relatedEventField: '', relationshipDirection: 'FROM_RELATED' }

function StatCard({ item, onPickField }) {
  return (
    <div className="settings-stat-box">
      <div className="settings-stat-box__header">
        <span className="settings-stat-box__title">{item.eventType}</span>
        <span className="settings-stat-box__count">{item.topFields.length} fields</span>
      </div>
      <div className="settings-stat-box__list">
        {item.topFields.length === 0 ? (
          <div className="settings-empty-state">Chưa có trường lạ</div>
        ) : item.topFields.map(field => (
          <button
            key={field.fieldName}
            type="button"
            className="settings-stat-item"
            onClick={() => onPickField(item.eventType, field.fieldName)}
          >
            <span className="settings-stat-item__field">{field.fieldName}</span>
            <span className="settings-stat-item__count">× {field.count}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

export default function SettingsPage() {
  const [dashboard, setDashboard] = useState([])
  const [mappings, setMappings] = useState([])
  const [customEventTypes, setCustomEventTypes] = useState([])
  const [options, setOptions] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(DEFAULT_FORM)
  const [customForm, setCustomForm] = useState({
    eventType: '',
    fields: [EMPTY_CUSTOM_FIELD],
  })
  const [customSaving, setCustomSaving] = useState(false)

  const relatedFields = useMemo(() => {
    if (!options?.relatedEventFields) return []
    const backend = options.relatedEventFields[form.eventType] || []
    // Client-side alias suggestions to cover common alternative field names
    const aliases = [
      'username','user','targetUser','accountName','actor',
      'hostname','host','targetHost','workstation',
      'address','ip','srcIp','dstIp','targetIp','srcEndpoint.ip',
      'name','domain','dstDomain','targetDomain',
      'hash','fileHash','targetFileHash',
      'url','targetUrl',
      'processName','process_path','procName','processPath',
      'resourceId','cloud_resource_id','targetCloudResourceId',
      'email','targetEmail',
      'cveId','targetCve',
      'eventId'
    ]
    const combined = Array.from(new Set([...backend, ...aliases]))
    return combined
  }, [options, form.eventType])

  const ENTITY_RELATED_ALIASES = {
    User: ['username','user','targetUser','accountName','actor','eventId'],
    Host: ['hostname','host','targetHost','workstation','eventId'],
    IP: ['address','ip','srcIp','dstIp','targetIp','sourceIp','eventId'],
    Domain: ['name','domain','dstDomain','targetDomain','eventId'],
    FileHash: ['hash','fileHash','targetFileHash','eventId'],
    Url: ['url','targetUrl','eventId'],
    Process: ['processName','process_path','procName','processPath','eventId'],
    CloudResource: ['resourceId','cloud_resource_id','targetCloudResourceId','eventId'],
    Email: ['address','email','targetEmail','eventId'],
    Cve: ['cveId','targetCve','eventId'],
  }

  const relatedOptionsFor = (entityType) => {
    const base = relatedFields || []
    if (!entityType) return base
    const allowed = ENTITY_RELATED_ALIASES[entityType] || []
    // keep order from `base`, filter by allowed (case-insensitive)
    const allowedSet = new Set(allowed.map(s => s.toLowerCase()))
    return base.filter(f => allowedSet.has(f.toLowerCase()))
  }

  const selectedRelatedField = useMemo(() => form.relatedEventField || '', [form.relatedEventField])

  const loadAll = async () => {
    setLoading(true)
    setError('')
    try {
      const [stats, mappingOptions, mappingList, customTypes] = await Promise.all([
        fetchUnknownFieldStats(5),
        fetchFieldMappingOptions(),
        fetchFieldMappings(),
        fetchCustomEventTypes(),
      ])
      setDashboard(stats)
      setOptions(mappingOptions)
      setMappings(mappingList)
      setCustomEventTypes(customTypes)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        await loadAll()
      } catch {
        if (active) setError('Không thể tải dữ liệu cài đặt')
      }
    }
    load()
    return () => { active = false }
  }, [])

  const resetForm = () => {
    setEditingId(null)
    setForm(DEFAULT_FORM)
  }

  const pickField = (eventType, sourceField) => {
    setEditingId(null)
    setForm(prev => ({
      ...prev,
      eventType,
      sourceField,
      relatedEventField: '',
    }))
  }

  const beginEdit = (mapping) => {
    setEditingId(mapping.id)
    setForm({
      eventType: mapping.eventType || 'AUTHENTICATION',
      sourceField: mapping.sourceField || '',
      entityType: mapping.entityType || 'User',
      relationshipType: mapping.relationshipType || '',
      relatedEntityType: mapping.relatedEntityType || 'Host',
      relatedEventField: mapping.relatedEventField || '',
      relationshipDirection: mapping.relationshipDirection || 'FROM_RELATED',
      enabled: Boolean(mapping.enabled),
    })
  }

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError('')
    try {
      const payload = {
        ...form,
        sourceField: form.sourceField.trim(),
        relationshipType: form.relationshipType.trim() || null,
        relatedEntityType: form.relationshipType.trim() ? form.relatedEntityType : null,
        relatedEventField: form.relationshipType.trim() ? form.relatedEventField.trim() : null,
      }
      if (editingId) {
        await updateFieldMapping(editingId, payload)
      } else {
        await createFieldMapping(payload)
      }
      resetForm()
      await loadAll()
    } catch (e) {
      setError(e.message)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (mapping) => {
    if (!window.confirm(`Xóa mapping cho ${mapping.eventType} / ${mapping.sourceField}?`)) return
    try {
      await deleteFieldMapping(mapping.id)
      await loadAll()
    } catch (e) {
      setError(e.message)
    }
  }

  const handleCustomSubmit = async (e) => {
    e.preventDefault()
    setCustomSaving(true)
    setError('')
    try {
      const payload = {
        eventType: customForm.eventType.trim(),
        fields: customForm.fields.filter(field => field.name.trim()).map(field => ({
          name: field.name.trim(),
          valueType: field.valueType || 'string',
          entityType: field.entityType || null,
          relationshipType: field.relationshipType?.trim() || null,
          relatedEntityType: field.relationshipType?.trim() ? field.relatedEntityType : null,
          relatedEventField: field.relationshipType?.trim() ? field.relatedEventField : null,
          relationshipDirection: field.relationshipType?.trim() ? field.relationshipDirection : 'FROM_RELATED',
        })),
      }
      await createCustomEventType(payload)
      setCustomForm({
        eventType: '',
        fields: [EMPTY_CUSTOM_FIELD],
      })
      await loadAll()
    } catch (e) {
      setError(e.message)
    } finally {
      setCustomSaving(false)
    }
  }

  const updateCustomField = (index, key, value) => {
    setCustomForm(prev => ({
      ...prev,
      fields: prev.fields.map((field, i) => i === index ? { ...field, [key]: value } : field),
    }))
  }

  const addCustomField = () => {
    setCustomForm(prev => ({ ...prev, fields: [...prev.fields, { ...EMPTY_CUSTOM_FIELD, name: '', valueType: 'string' }] }))
  }

  const removeCustomField = (index) => {
    setCustomForm(prev => ({ ...prev, fields: prev.fields.filter((_, i) => i !== index) }))
  }

  const eventTypes = options?.eventTypes || ['AUTHENTICATION', 'PROCESS', 'NETWORK', 'THREAT']
  const entityTypes = options?.entityTypes || []
  const relationshipTypes = options?.relationshipTypes || []
  const directions = options?.relationshipDirections || ['FROM_RELATED', 'TO_RELATED']

  return (
    <div className="settings-shell">
      <section className="settings-hero">
        <div>
          <div className="settings-kicker">Configuration Center</div>
          <div className="page-title">Field Mapping & Custom Event Setup</div>
          <div className="page-sub">Tạo mapping cho trường lạ, định nghĩa custom event type và thiết lập relationship để graph tự sinh node/edge.</div>
        </div>
        <div className="settings-hero-actions">
          <div className="settings-pill">⚙️ {mappings.length} mappings</div>
          <div className="settings-pill">🧩 {customEventTypes.length} custom types</div>
          <div className="settings-pill">🔎 {dashboard.length} unknown groups</div>
        </div>
      </section>

      {error && <div className="pf-error settings-error">{error}</div>}

      <section className="settings-stats">
        <div className="settings-stat-card">
          <div className="settings-stat-card__label">Unknown fields</div>
          <div className="settings-stat-card__value">{dashboard.length}</div>
          <div className="settings-stat-card__hint">Top groups by event type</div>
        </div>
        <div className="settings-stat-card">
          <div className="settings-stat-card__label">Mappings</div>
          <div className="settings-stat-card__value">{mappings.length}</div>
          <div className="settings-stat-card__hint">Active graph rules</div>
        </div>
        <div className="settings-stat-card">
          <div className="settings-stat-card__label">Custom event types</div>
          <div className="settings-stat-card__value">{customEventTypes.length}</div>
          <div className="settings-stat-card__hint">Templates for incoming payloads</div>
        </div>
      </section>

      <div className="settings-grid">
        <section className="settings-card settings-card--accent">
          <div className="card-header">
            <span className="card-title">Unknown fields to map</span>
            <span className="card-count">Top 5 / event type</span>
          </div>
          {loading ? (
            <div className="loading">Đang tải thống kê…</div>
          ) : (
            <div className="settings-stat-stack">
              {dashboard.map(item => (
                <StatCard key={item.eventType} item={item} onPickField={pickField} />
              ))}
            </div>
          )}
        </section>

        <div className="settings-stack">
          <section className="settings-card">
            <div className="card-header">
              <span className="card-title">{editingId ? 'Sửa mapping' : 'Tạo mapping mới'}</span>
              <button className="gv-btn" onClick={resetForm}>Reset</button>
            </div>

            <form onSubmit={submit} className="settings-form">
              <div className="settings-form-grid">
                <label className="settings-form-field">
                  <span className="pf-opt-label">Event type</span>
                  <select
                    className="pf-select"
                    value={form.eventType}
                    onChange={e => setForm(prev => ({
                      ...prev,
                      eventType: e.target.value,
                      relatedEventField: '',
                    }))}
                  >
                    {eventTypes.map(type => <option key={type} value={type}>{type}</option>)}
                  </select>
                </label>

                <label className="settings-form-field">
                  <span className="pf-opt-label">Trường lạ</span>
                  <input
                    className="pf-input"
                    value={form.sourceField}
                    onChange={e => setForm(prev => ({ ...prev, sourceField: e.target.value }))}
                    placeholder="vd: suspicious_user_agent"
                  />
                </label>

                <label className="settings-form-field">
                  <span className="pf-opt-label">Entity type</span>
                  <select
                    className="pf-select"
                    value={form.entityType}
                    onChange={e => setForm(prev => ({ ...prev, entityType: e.target.value }))}
                  >
                    {entityTypes.map(type => <option key={type} value={type}>{type}</option>)}
                  </select>
                </label>

                <label className="settings-form-field settings-form-field--wide">
                  <span className="pf-opt-label">Relationship type</span>
                  <input
                    className="pf-input"
                    list="relationship-types"
                    value={form.relationshipType}
                    onChange={e => setForm(prev => ({ ...prev, relationshipType: e.target.value }))}
                    placeholder="vd: ACCESSED, DETECTED_ON"
                  />
                  <datalist id="relationship-types">
                    {relationshipTypes.map(type => <option key={type} value={type} />)}
                  </datalist>
                </label>
              </div>

              {form.relationshipType.trim() && (
                <div className="settings-form-grid settings-form-grid--nested">
                  <label className="settings-form-field">
                    <span className="pf-opt-label">Related entity type</span>
                    <select
                      className="pf-select"
                      value={form.relatedEntityType}
                      onChange={e => setForm(prev => ({ ...prev, relatedEntityType: e.target.value, relatedEventField: '' }))}
                    >
                      {entityTypes.map(type => <option key={type} value={type}>{type}</option>)}
                    </select>
                  </label>

                  <label className="settings-form-field">
                    <span className="pf-opt-label">Related event field</span>
                    <select
                      className="pf-select"
                      value={selectedRelatedField}
                      onChange={e => setForm(prev => ({ ...prev, relatedEventField: e.target.value }))}
                    >
                      <option value="">-- tự suy từ entity --</option>
                      {relatedOptionsFor(form.relatedEntityType).map(field => <option key={field} value={field}>{field}</option>)}
                    </select>
                    <div className="pf-help">Bỏ trống để backend tự suy field phù hợp với Entity đích.</div>
                  </label>

                  <label className="settings-form-field">
                    <span className="pf-opt-label">Direction</span>
                    <select
                      className="pf-select"
                      value={form.relationshipDirection}
                      onChange={e => setForm(prev => ({ ...prev, relationshipDirection: e.target.value }))}
                    >
                      {directions.map(direction => <option key={direction} value={direction}>{direction}</option>)}
                    </select>
                  </label>
                </div>
              )}

              <label className="settings-toggle">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={e => setForm(prev => ({ ...prev, enabled: e.target.checked }))}
                />
                <span>Enable mapping</span>
              </label>

              <button className="pf-run-btn" type="submit" disabled={saving || !form.sourceField.trim()}>
                {saving ? 'Đang lưu…' : editingId ? 'Cập nhật mapping' : 'Lưu mapping'}
              </button>
            </form>
          </section>

          <section className="settings-card">
            <div className="card-header">
              <span className="card-title">Tạo eventType mới</span>
              <span className="card-count">Mỗi field tự định nghĩa entity & relationship</span>
            </div>

            <form onSubmit={handleCustomSubmit} className="settings-form">
              <label className="settings-form-field settings-form-field--wide">
                <span className="pf-opt-label">Tên eventType</span>
                <input
                  className="pf-input"
                  value={customForm.eventType}
                  onChange={e => setCustomForm(prev => ({ ...prev, eventType: e.target.value }))}
                  placeholder="vd: MFA_FAILURE"
                />
              </label>

              <div className="settings-field-stack">
                <div className="settings-section-label">Fields</div>
                {customForm.fields.map((field, index) => (
                  <div key={index} className="settings-field-card">
                    <div className="settings-field-card__header">
                      <div className="settings-field-card__title">Field #{index + 1}</div>
                      <button type="button" className="gv-btn" onClick={() => removeCustomField(index)}>✕</button>
                    </div>

                    <div className="settings-form-grid settings-form-grid--nested">
                      <label className="settings-form-field settings-form-field--wide">
                        <span className="pf-opt-label">Tên trường</span>
                        <input
                          className="pf-input"
                          value={field.name}
                          onChange={e => updateCustomField(index, 'name', e.target.value)}
                          placeholder="Tên trường"
                        />
                      </label>
                      <label className="settings-form-field">
                        <span className="pf-opt-label">Value type</span>
                        <select
                          className="pf-select"
                          value={field.valueType}
                          onChange={e => updateCustomField(index, 'valueType', e.target.value)}
                        >
                          <option value="string">string</option>
                          <option value="number">number</option>
                          <option value="boolean">boolean</option>
                          <option value="object">object</option>
                        </select>
                      </label>
                    </div>

                    <div className="settings-form-grid settings-form-grid--nested">
                      <label className="settings-form-field">
                        <span className="pf-opt-label">Tạo entity gì</span>
                        <select
                          className="pf-select"
                          value={field.entityType || 'User'}
                          onChange={e => updateCustomField(index, 'entityType', e.target.value)}
                        >
                          {entityTypes.map(type => <option key={type} value={type}>{type}</option>)}
                        </select>
                      </label>

                      <label className="settings-form-field settings-form-field--wide">
                        <span className="pf-opt-label">Relationship</span>
                        <input
                          className="pf-input"
                          list="relationship-types"
                          value={field.relationshipType || ''}
                          onChange={e => updateCustomField(index, 'relationshipType', e.target.value)}
                          placeholder="vd: ACCESSED, DETECTED_ON"
                        />
                      </label>
                    </div>

                    {field.relationshipType?.trim() && (
                      <div className="settings-form-grid settings-form-grid--nested">
                        <label className="settings-form-field">
                          <span className="pf-opt-label">Entity đích</span>
                          <select
                            className="pf-select"
                            value={field.relatedEntityType || 'Host'}
                            onChange={e => updateCustomField(index, 'relatedEntityType', e.target.value)}
                          >
                            {entityTypes.map(type => <option key={type} value={type}>{type}</option>)}
                          </select>
                        </label>

                        <label className="settings-form-field">
                          <span className="pf-opt-label">Field liên quan</span>
                          <select
                            className="pf-select"
                            value={field.relatedEventField || ''}
                            onChange={e => updateCustomField(index, 'relatedEventField', e.target.value)}
                          >
                            <option value="">-- tự suy từ entity --</option>
                            {relatedOptionsFor(field.relatedEntityType).map(fieldName => <option key={fieldName} value={fieldName}>{fieldName}</option>)}
                          </select>
                          <div className="pf-help">Bỏ trống để backend tự suy field phù hợp với Entity đích.</div>
                        </label>

                        <label className="settings-form-field">
                          <span className="pf-opt-label">Direction</span>
                          <select
                            className="pf-select"
                            value={field.relationshipDirection || 'FROM_RELATED'}
                            onChange={e => updateCustomField(index, 'relationshipDirection', e.target.value)}
                          >
                            {directions.map(direction => <option key={direction} value={direction}>{direction}</option>)}
                          </select>
                        </label>
                      </div>
                    )}
                  </div>
                ))}
                <button type="button" className="gv-btn" onClick={addCustomField}>+ Thêm field</button>
              </div>

              <div className="settings-preview">
                <div className="settings-section-label">Preview JSON</div>
                <pre>
{JSON.stringify({
  eventType: customForm.eventType.trim() || 'YOUR_EVENT_TYPE',
  fields: customForm.fields.filter(field => field.name.trim()).map(field => ({
    name: field.name.trim(),
    valueType: field.valueType || 'string',
    entityType: field.entityType || null,
    relationshipType: field.relationshipType?.trim() || null,
    relatedEntityType: field.relationshipType?.trim() ? field.relatedEntityType : null,
    relatedEventField: field.relationshipType?.trim() ? field.relatedEventField : null,
    relationshipDirection: field.relationshipType?.trim() ? field.relationshipDirection : 'FROM_RELATED',
  })),
}, null, 2)}
                </pre>
              </div>

              <button className="pf-run-btn" type="submit" disabled={customSaving || !customForm.eventType.trim()}>
                {customSaving ? 'Đang tạo…' : 'Tạo eventType'}
              </button>
            </form>
          </section>
        </div>
      </div>

      <section className="settings-card">
        <div className="card-header">
          <span className="card-title">Custom eventTypes đã tạo</span>
          <span className="card-count">{customEventTypes.length}</span>
        </div>
        {customEventTypes.length === 0 ? (
          <div className="settings-empty-state">Chưa có custom eventType nào.</div>
        ) : (
          <div className="settings-list-stack">
            {customEventTypes.map(item => (
              <div key={item.id} className="settings-list-card">
                <div className="settings-list-card__header">
                  <span className="settings-list-card__title">{item.eventType}</span>
                  <span className="settings-list-card__count">{(item.fields || []).length} fields</span>
                </div>
                <div className="settings-list-card__body">
                  {(item.fields || []).map(field => (
                    <div key={field.name} className="settings-list-chip">
                      <div className="settings-list-chip__name">{field.name} · {field.valueType}</div>
                      <div className="settings-list-chip__meta">
                        {field.entityType ? `entity: ${field.entityType}` : 'entity: —'}
                        {field.relationshipType ? ` · rel: ${field.relationshipType}` : ''}
                        {field.relatedEntityType ? ` · related: ${field.relatedEntityType}` : ''}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="settings-card">
        <div className="card-header">
          <span className="card-title">Mappings hiện có</span>
          <span className="card-count">{mappings.length}</span>
        </div>
        {mappings.length === 0 ? (
          <div className="settings-empty-state">Chưa có mapping nào.</div>
        ) : (
          <div className="settings-table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Event</th>
                  <th>Field</th>
                  <th>Entity</th>
                  <th>Relationship</th>
                  <th>Related</th>
                  <th>Direction</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {mappings.map(mapping => (
                  <tr key={mapping.id}>
                    <td>{mapping.eventType}</td>
                    <td><span className="mono">{mapping.sourceField}</span></td>
                    <td>{mapping.entityType}</td>
                    <td>{mapping.relationshipType || '—'}</td>
                    <td>{mapping.relatedEntityType && mapping.relatedEventField ? `${mapping.relatedEntityType} / ${mapping.relatedEventField}` : '—'}</td>
                    <td>{mapping.relationshipDirection || '—'}</td>
                    <td>{mapping.enabled ? 'Enabled' : 'Disabled'}</td>
                    <td className="settings-table-actions">
                      <button className="gv-btn" type="button" onClick={() => beginEdit(mapping)}>Edit</button>
                      <button className="gv-btn" type="button" onClick={() => remove(mapping)}>Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}