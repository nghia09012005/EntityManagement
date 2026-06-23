import { useRef, useState } from 'react'
import { uploadLogFile, ingestLog } from '../api'

const ACCEPT = '.log,.txt,.json'

export default function UploadPanel({ onUploaded }) {
  const inputRef = useRef(null)
  const [fileStatus,  setFileStatus]  = useState(null)   // null | 'uploading' | result[]
  const [alertText,   setAlertText]   = useState('')
  const [alertStatus, setAlertStatus] = useState(null)   // null | 'sending' | {ok, msg}

  // ── File upload ──────────────────────────────────────────────────────────
  const handleFiles = async (files) => {
    if (!files?.length) return
    setFileStatus('uploading')
    const results = []
    for (const file of files) {
      try {
        const res = await uploadLogFile(file)
        results.push({ ok: true, name: file.name, fileName: res.fileName })
      } catch (e) {
        results.push({ ok: false, name: file.name, error: e.message })
      }
    }
    setFileStatus(results)
    if (results.some(r => r.ok)) onUploaded?.()
  }

  const onDrop = (e) => { e.preventDefault(); handleFiles(e.dataTransfer.files) }

  // ── Alert free text ──────────────────────────────────────────────────────
  const handleSendAlert = async () => {
    const text = alertText.trim()
    if (!text) return
    setAlertStatus('sending')
    try {
      await ingestLog(text)
      setAlertStatus({ ok: true })
      setAlertText('')
      onUploaded?.()
    } catch (e) {
      setAlertStatus({ ok: false, msg: e.message })
    }
  }

  return (
    <div style={{ display: 'flex', gap: 16, marginBottom: 24, alignItems: 'flex-start' }}>

      {/* File upload */}
      <div className="upload-panel" style={{ flex: 1 }}>
        <div
          className="upload-zone"
          onDragOver={(e) => e.preventDefault()}
          onDrop={onDrop}
        >
          <input
            ref={inputRef}
            type="file"
            accept={ACCEPT}
            multiple
            style={{ display: 'none' }}
            onChange={(e) => handleFiles(e.target.files)}
            onClick={(e) => { e.target.value = null }}
          />
          <span className="upload-icon">↑</span>
          <span className="upload-label">Kéo thả file log vào đây</span>
          <span className="upload-hint">.log · .txt · .json — nhiều file cùng lúc</span>
          <button
            className="upload-btn"
            onClick={() => inputRef.current.click()}
          >
            Chọn file
          </button>
        </div>

        {fileStatus === 'uploading' && (
          <div className="upload-status uploading">Đang upload…</div>
        )}
        {Array.isArray(fileStatus) && (
          <div className="upload-results">
            {fileStatus.map((r, i) => (
              <div key={i} className={`upload-result ${r.ok ? 'ok' : 'err'}`}>
                {r.ok ? `✓ ${r.name} → đã gửi pipeline` : `✗ ${r.name}: ${r.error}`}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Free text alert */}
      <div className="upload-panel" style={{ flex: 1 }}>
        <div style={{ padding: '12px 14px' }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: '#a0aec0', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
            Nhập Alert (Free Text)
          </div>
          <textarea
            value={alertText}
            onChange={(e) => { setAlertText(e.target.value); setAlertStatus(null) }}
            onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) handleSendAlert() }}
            placeholder="Mô tả alert bằng ngôn ngữ tự nhiên, ví dụ: Phát hiện đăng nhập đáng ngờ từ IP 192.168.1.100 lúc 3 giờ sáng vào máy chủ SRV-DC01..."
            style={{
              width: '100%', minHeight: 90, resize: 'vertical',
              background: '#0d1117', color: '#c9d1d9',
              border: '1px solid #30363d', borderRadius: 6,
              padding: '8px 10px', fontSize: 13, fontFamily: 'inherit',
              outline: 'none', boxSizing: 'border-box',
            }}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
            <span style={{ fontSize: 11, color: '#4a5568' }}>Ctrl+Enter để gửi nhanh</span>
            <button
              className="upload-btn"
              disabled={!alertText.trim() || alertStatus === 'sending'}
              onClick={handleSendAlert}
              style={{ opacity: !alertText.trim() ? 0.4 : 1 }}
            >
              {alertStatus === 'sending' ? 'Đang gửi…' : 'Gửi Alert'}
            </button>
          </div>
          {alertStatus && alertStatus !== 'sending' && (
            <div className={`upload-result ${alertStatus.ok ? 'ok' : 'err'}`} style={{ marginTop: 8 }}>
              {alertStatus.ok ? '✓ Alert đã gửi vào pipeline (LLM sẽ xử lý)' : `✗ ${alertStatus.msg}`}
            </div>
          )}
        </div>
      </div>

    </div>
  )
}
