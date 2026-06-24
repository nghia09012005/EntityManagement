import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { loginUser } from '../api'

export default function LoginPage({ onLoginSuccess }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = (e) => {
    e.preventDefault()
    setError('')

    if (!username.trim() || !password.trim()) {
      setError('Vui lòng điền đầy đủ Tên đăng nhập và Mật khẩu.')
      return
    }

    setLoading(true)

    loginUser(username.trim(), password)
      .then((data) => {
        localStorage.setItem('soc_token', data.token)
        localStorage.setItem('soc_current_user', JSON.stringify(data))
        onLoginSuccess(data)
        navigate('/')
      })
      .catch((err) => {
        setError(err.message || 'Tên đăng nhập hoặc Mật khẩu không chính xác.')
      })
      .finally(() => {
        setLoading(false)
      })
  }

  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-header">
          <h1 className="auth-title">Đăng Nhập</h1>
          <p className="auth-subtitle">Cổng quản trị SOC Entity Graph</p>
        </div>

        {error && <div className="auth-error">{error}</div>}

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
              disabled={loading}
              autoComplete="username"
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
              placeholder="Nhập mật khẩu"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
              autoComplete="current-password"
            />
          </div>

          <button type="submit" className="auth-btn" disabled={loading}>
            {loading ? 'Đang xác thực...' : 'Đăng Nhập'}
          </button>
        </form>

        <div className="auth-toggle">
          Chưa có tài khoản?{' '}
          <span className="auth-toggle-link" onClick={() => navigate('/register')}>
            Đăng ký ngay
          </span>
        </div>
      </div>
    </div>
  )
}
