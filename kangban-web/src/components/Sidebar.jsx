import { Activity, CircleHelp, ClipboardPenLine, FileText, Grid2X2, LogIn, LogOut, MessageCircle, Menu, Pill, Settings, UserRound, UsersRound, X } from 'lucide-react';
import { DEFAULT_AVATAR_URL, getUserDisplayName, NAV_ITEMS } from '../data.js';
import { useAuth } from '../context/AuthContext.jsx';

export default function Sidebar({ pageId, onNavigate, open, onClose }) {
  const { user, isAuthenticated, logout } = useAuth();
  const activePageId = pageId === 'record-detail' ? 'records' : pageId === 'medication-add' ? 'medications' : pageId;
  const icons = { home: Grid2X2, profile: UserRound, family: UsersRound, 'health-record': ClipboardPenLine, trends: Activity, records: FileText, consultation: MessageCircle, medications: Pill, settings: Settings };

  const displayName = isAuthenticated ? getUserDisplayName(user) : '未登录';
  const userAvatar = user?.avatarUrl || DEFAULT_AVATAR_URL;

  return <aside className={`sidebar ${open ? 'open' : ''}`}>
    <div className="brand"><span className="brand-mark">+</span><span className="brand-copy"><strong>患者中心</strong><small>康伴医疗助手 v2.1</small></span><button className="mobile-menu-button" aria-label="关闭菜单" onClick={onClose}><X size={18} /></button></div>
    <nav className="nav-list" aria-label="主导航">
      {NAV_ITEMS.map(item => { const Icon = icons[item.id]; return <button key={item.id} className={`nav-item ${activePageId === item.id ? 'active' : ''}`} aria-current={activePageId === item.id ? 'page' : undefined} onClick={() => onNavigate(item.id)}><span className="nav-icon"><Icon size={16} strokeWidth={1.8} /></span>{item.label}</button>; })}
    </nav>
    <div className="sidebar-spacer" />
    <div className="sidebar-bottom">
      <button className="nav-item" onClick={() => { /* TODO: 帮助页面 */ window.dispatchEvent(new CustomEvent('app:success', { detail: '帮助文档正在建设中' })); }}><span className="nav-icon"><CircleHelp size={16} /></span>帮助</button>
      {isAuthenticated && <button className="nav-item" onClick={() => { if (window.confirm('确定退出登录？')) logout(); }}><span className="nav-icon"><LogOut size={15} /></span>退出登录</button>}
    </div>
    <button className="profile-mini" type="button" onClick={() => onNavigate(isAuthenticated ? 'profile' : 'login')}>
      <img className="avatar" src={userAvatar} alt={isAuthenticated ? '当前用户头像' : '访客头像'} />
      <span><strong>{displayName}</strong><small>{isAuthenticated ? '个人账户' : '登录 / 注册'}</small></span>
      {isAuthenticated ? <Menu size={14} color="var(--muted)" /> : <LogIn size={15} color="var(--primary)" />}
    </button>
  </aside>;
}
