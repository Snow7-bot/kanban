import { Bell, Menu, Search, Settings } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import { IconButton } from './UI.jsx';
import { DEFAULT_AVATAR_URL, getUserDisplayName } from '../data.js';

export default function TopBar({ onMenu, onNavigate, compact = false }) {
  const { user } = useAuth();
  const displayName = getUserDisplayName(user);
  const userAvatar = user?.avatarUrl || DEFAULT_AVATAR_URL;

  return <header className="top-utility">
    <button className="mobile-menu-button" aria-label="打开菜单" onClick={onMenu}><Menu size={18} /></button>
    <div className="search-box"><Search size={14} /><input aria-label="搜索" placeholder={compact ? '搜索记录...' : '搜索'} /></div>
    <IconButton label="通知" onClick={() => window.dispatchEvent(new CustomEvent('app:success', { detail: '暂无新通知' }))}><Bell size={15} /></IconButton>
    <IconButton label="设置" onClick={() => onNavigate?.('settings')}><Settings size={15} /></IconButton>
    <div className="user-mini"><span>{displayName}</span><img className="avatar" src={userAvatar} alt="当前用户头像" /></div>
  </header>;
}
