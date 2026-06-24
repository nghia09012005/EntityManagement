import { useState } from 'react'
import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom'
import EntityListPage from './pages/EntityListPage'
import EntityDetailPage from './pages/EntityDetailPage'
import PathFinderPage from './pages/PathFinderPage'
import DlqPage from './pages/DlqPage'
import UploadPanel from './components/UploadPanel'

function AppContent() {
  const [uploadKey, setUploadKey] = useState(0)
  const location = useLocation()
  const isHome = location.pathname === '/'

  return (
    <>
      <nav className="navbar">
        <Link to="/" className="navbar-brand">SOC Entity Graph</Link>
        <span className="navbar-sub">Security Operations Center</span>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
          <Link to="/"      className={`nav-link ${isHome ? 'active' : ''}`}>Entities</Link>
          <Link to="/paths" className={`nav-link ${location.pathname === '/paths' ? 'active' : ''}`}>Path Finder</Link>
          <Link to="/dlq"   className={`nav-link ${location.pathname === '/dlq'   ? 'active' : ''}`}>Dead Letters</Link>
        </div>
      </nav>
      <main className="main">
        {isHome && <UploadPanel onUploaded={() => setUploadKey(k => k + 1)} />}
        <Routes>
          <Route path="/" element={<EntityListPage key={uploadKey} />} />
          <Route path="/entity/:type/:value" element={<EntityDetailPage />} />
          <Route path="/paths" element={<PathFinderPage />} />
          <Route path="/dlq"   element={<DlqPage />} />
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
