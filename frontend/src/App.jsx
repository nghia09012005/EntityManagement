import { useState } from 'react'
import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom'
import EntityListPage from './pages/EntityListPage'
import EntityDetailPage from './pages/EntityDetailPage'
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
      </nav>
      <main className="main">
        {isHome && <UploadPanel onUploaded={() => setUploadKey(k => k + 1)} />}
        <Routes>
          <Route path="/" element={<EntityListPage key={uploadKey} />} />
          <Route path="/entity/:type/:value" element={<EntityDetailPage />} />
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
