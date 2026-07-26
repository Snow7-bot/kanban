import { useState } from 'react';
import { Menu } from 'lucide-react';
import Sidebar from './Sidebar.jsx';
import TopBar from './TopBar.jsx';

export default function AppShell({ pageId, onNavigate, children, showTopBar = true }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = (nextPage) => { onNavigate(nextPage); setMenuOpen(false); };
  return <div className={`app-shell ${showTopBar ? '' : 'app-shell-sidebar-only'}`}>
    <Sidebar pageId={pageId} onNavigate={navigate} open={menuOpen} onClose={() => setMenuOpen(false)} />
    <div className="page-canvas">
      {showTopBar ? <TopBar onMenu={() => setMenuOpen(true)} onNavigate={navigate} compact={pageId === 'records'} /> : <button className="shell-menu-fab" aria-label="打开导航" onClick={() => setMenuOpen(true)}><Menu size={20} /></button>}
      {children}
    </div>
  </div>;
}
