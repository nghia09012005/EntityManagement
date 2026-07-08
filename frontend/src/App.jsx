import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Link, useLocation, Navigate, useNavigate } from 'react-router-dom'
import EntityListPage from './pages/EntityListPage'
import EntityDetailPage from './pages/EntityDetailPage'
import PathFinderPage from './pages/PathFinderPage'
import UnknownFieldsPage from './pages/UnknownFieldsPage'
import SettingsPage from './pages/SettingsPage'
import DlqPage from './pages/DlqPage'
import IncidentsPage from './pages/IncidentsPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import UploadPanel from './components/UploadPanel'

function ProtectedRoute({ children, user }) {
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return children
}

function AppContent() {
  const [uploadKey, setUploadKey] = useState(0)
  const [user, setUser] = useState(null)
  const [initialized, setInitialized] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    const storedUser = localStorage.getItem('soc_current_user')
    const token = localStorage.getItem('soc_token')
    if (storedUser && token) {
      try {
        setUser(JSON.parse(storedUser))
      } catch {
        localStorage.removeItem('soc_current_user')
        localStorage.removeItem('soc_token')
      }
    }
    setInitialized(true)
  }, [])

  const handleLoginSuccess = (loggedInUser) => {
    setUser(loggedInUser)
  }

  const handleLogout = () => {
    localStorage.removeItem('soc_token')
    localStorage.removeItem('soc_current_user')
    setUser(null)
    navigate('/login')
  }

  if (!initialized) {
    return <div className="loading">Đang tải cấu hình...</div>
  }

  const isHome = location.pathname === '/'
  const isAuthPage = location.pathname === '/login' || location.pathname === '/register'

  return (
    <>
      {!isAuthPage && (
        <nav className="navbar">
          <Link to="/" className="navbar-brand">SOC Entity Graph</Link>
          <span className="navbar-sub">Security Operations Center</span>
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 16, alignItems: 'center' }}>
            <div style={{ display: 'flex', gap: 4 }}>
              <Link to="/"          className={`nav-link ${isHome ? 'active' : ''}`}>Entities</Link>
              <Link to="/paths"     className={`nav-link ${location.pathname === '/paths'     ? 'active' : ''}`}>Path Finder</Link>
              <Link to="/unknown-fields" className={`nav-link ${location.pathname === '/unknown-fields' ? 'active' : ''}`}>Unknown Fields</Link>
              <Link to="/settings/field-mappings" className={`nav-link ${location.pathname.startsWith('/settings') ? 'active' : ''}`}>Settings</Link>
              <Link to="/incidents" className={`nav-link ${location.pathname === '/incidents' ? 'active' : ''}`}>Incidents</Link>
              <Link to="/dlq"       className={`nav-link ${location.pathname === '/dlq'       ? 'active' : ''}`}>Dead Letters</Link>
            </div>
            {user && (
              <div className="navbar-user">
                Hi, <span className="navbar-username">{user.username}</span>
                <button className="logout-btn" onClick={handleLogout}>Đăng xuất</button>
              </div>
            )}
          </div>
        </nav>
      )}
      <main className="main">
        {isHome && user && <UploadPanel onUploaded={() => setUploadKey(k => k + 1)} />}
        <Routes>
          <Route
            path="/login"
            element={user ? <Navigate to="/" replace /> : <LoginPage onLoginSuccess={handleLoginSuccess} />}
          />
          <Route
            path="/register"
            element={user ? <Navigate to="/" replace /> : <RegisterPage />}
          />
          <Route
            path="/"
            element={
              <ProtectedRoute user={user}>
                <EntityListPage key={uploadKey} />
              </ProtectedRoute>
            }
          />
          <Route
            path="/entity/:type/:value"
            element={
              <ProtectedRoute user={user}>
                <EntityDetailPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/paths"
            element={
              <ProtectedRoute user={user}>
                <PathFinderPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/unknown-fields"
            element={
              <ProtectedRoute user={user}>
                <UnknownFieldsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings/field-mappings"
            element={
              <ProtectedRoute user={user}>
                <SettingsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/incidents"
            element={
              <ProtectedRoute user={user}>
                <IncidentsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dlq"
            element={
              <ProtectedRoute user={user}>
                <DlqPage />
              </ProtectedRoute>
            }
          />
        </Routes>
      </main>
    </>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  )
}
