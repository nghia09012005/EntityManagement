import { useRef, useState } from 'react'
import { uploadLogFile } from '../api'

const ACCEPT = '.log,.txt,.json'

export default function UploadPanel({ onUploaded }) {
  const inputRef = useRef(null)
  const [status, setStatus] = useState(null) // null | 'uploading' | {ok, msg}

  const handleFiles = async (files) => {
    if (!files?.length) return
    setStatus('uploading')
    const results = []
    for (const file of files) {
      try {
        const res = await uploadLogFile(file)
        results.push({ ok: true, name: file.name, fileName: res.fileName })
      } catch (e) {
        results.push({ ok: false, name: file.name, error: e.message })
      }
    }
    setStatus(results)
    if (results.some(r => r.ok)) onUploaded?.()
  }

  const onChange = (e) => handleFiles(e.target.files)

  const onDrop = (e) => {
    e.preventDefault()
    handleFiles(e.dataTransfer.files)
  }

  return (
    <div className="upload-panel">
      <div
        className="upload-zone"
        onClick={() => inputRef.current.click()}
        onDragOver={(e) => e.preventDefault()}
        onDrop={onDrop}
      >
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          multiple
          style={{ display: 'none' }}
          onChange={onChange}
          onClick={(e) => { e.target.value = null }}
        />
        <span className="upload-icon">↑</span>
        <span className="upload-label">
          Kéo thả hoặc <strong>click</strong> để upload file log
        </span>
        <span className="upload-hint">.log · .txt · .json — nhiều file cùng lúc</span>
      </div>

      {status === 'uploading' && (
        <div className="upload-status uploading">Đang upload…</div>
      )}

      {Array.isArray(status) && (
        <div className="upload-results">
          {status.map((r, i) => (
            <div key={i} className={`upload-result ${r.ok ? 'ok' : 'err'}`}>
              {r.ok
                ? `✓ ${r.name} → đã gửi pipeline (${r.fileName})`
                : `✗ ${r.name}: ${r.error}`}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
