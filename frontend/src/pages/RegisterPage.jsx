import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { registerUser } from '../api'

export default function RegisterPage() {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')

    if (!username.trim() || !email.trim() || !password.trim() || !confirmPassword.trim()) {
      setError('Vui lòng điền tất cả các trường thông tin.')
      return
    }

    if (password !== confirmPassword) {
      setError('Mật khẩu và Xác nhận mật khẩu không khớp.')
      return
    }

    if (password.length < 6) {
      setError('Mật khẩu phải chứa ít nhất 6 ký tự.')
      return
    }

    setLoading(true)

    registerUser(username.trim(), email.trim(), password)
      .then(() => {
        setSuccess('Đăng ký tài khoản thành công! Đang chuyển hướng...')
        setTimeout(() => {
          navigate('/login')
        }, 1500)
      })
      .catch((err) => {
        setError(err.message || 'Đăng ký không thành công.')
        setLoading(false)
      })
  }

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">Đăng Ký</h1>
          <p className="auth-subtitle">Tạo tài khoản Analyst mới</p>
        </div>

        {error && <div className="auth-error">{error}</div>}
        {success && <div className="auth-success">{success}</div>}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-field">
            <label className="auth-label" htmlFor="username">
              Tên đăng nhập
            </label>
            <input
              type="text"
              id="username"
              className="auth-input"
              placeholder="Nhập tên đăng nhập"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={loading || !!success}
              autoComplete="username"
            />
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="email">
              Địa chỉ Email
            </label>
            <input
              type="email"
              id="email"
              className="auth-input"
              placeholder="Nhập email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading || !!success}
              autoComplete="email"
            />
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="password">
              Mật khẩu
            </label>
            <input
              type="password"
              id="password"
              className="auth-input"
              placeholder="Nhập mật khẩu (tối thiểu 6 ký tự)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading || !!success}
              autoComplete="new-password"
            />
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="confirmPassword">
              Xác nhận mật khẩu
            </label>
            <input
              type="password"
              id="confirmPassword"
              className="auth-input"
              placeholder="Nhập lại mật khẩu"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={loading || !!success}
              autoComplete="new-password"
            />
          </div>

          <button type="submit" className="auth-btn" disabled={loading || !!success}>
            {loading ? 'Đang tạo tài khoản...' : 'Đăng Ký'}
          </button>
        </form>

        <div className="auth-toggle">
          Đã có tài khoản?{' '}
          <span className="auth-toggle-link" onClick={() => navigate('/login')}>
            Đăng nhập ngay
          </span>
        </div>
      </div>
    </div>
  )
}
