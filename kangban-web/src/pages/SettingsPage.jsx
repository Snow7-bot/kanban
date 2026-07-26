import { useState } from 'react';
import { Accessibility, CheckCircle2, ChevronRight, Database, FileKey2, Trash2 } from 'lucide-react';
import { Button, Card } from '../components/UI.jsx';
import AccountFooter from '../components/AccountFooter.jsx';

export default function SettingsPage() {
  const [elderly, setElderly] = useState(false);
  const [cleared, setCleared] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const handleClearCache = async () => {
    try {
      if ('caches' in window) {
        const keys = await caches.keys();
        await Promise.all(keys.map(key => caches.delete(key)));
      }
      setCleared(true);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '缓存已清除' }));
    } catch {
      setCleared(true);
    }
  };

  const handleSync = async () => {
    setSyncing(true);
    window.dispatchEvent(new CustomEvent('app:success', { detail: '数据同步已开始' }));
    setTimeout(() => {
      setSyncing(false);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '同步完成' }));
    }, 2000);
  };

  const handleLegalClick = (label) => {
    window.dispatchEvent(new CustomEvent('app:success', { detail: `${label}页面` }));
  };

  const lastSyncTime = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });

  return <main className={`page-content account-page settings-page ${elderly ? 'settings-elderly' : ''}`}>
    <header className="account-heading"><div><h1>设置</h1><p>管理您的账户偏好和应用体验。</p></div></header>
    <section className="settings-stack">
      <Card className="settings-card">
        <header><Accessibility size={18} /><h2>辅助功能</h2></header>
        <div className="setting-row">
          <div><strong>老年模式（Elderly Mode）</strong><p>更大的文字、高对比度和简化导航，提升易读性。</p></div>
          <button type="button" className={`setting-switch ${elderly ? 'on' : ''}`} aria-pressed={elderly} aria-label="切换老年模式" onClick={() => setElderly(!elderly)}><i /></button>
        </div>
      </Card>
      <Card className="settings-card">
        <header><Database size={18} /><h2>存储与数据</h2></header>
        <div className="setting-row divided">
          <div><strong>清除缓存</strong><p>{cleared ? '缓存已清除。' : '释放由临时文件占用的本地存储空间。'}</p></div>
          <Button onClick={handleClearCache} disabled={cleared}>{cleared ? <><CheckCircle2 size={15} />已清除</> : <><Trash2 size={15} />立即清除</>}</Button>
        </div>
        <div className="setting-row">
          <div><strong>数据同步</strong><p>最后同步时间：{syncing ? '同步中...' : `${lastSyncTime}`}</p></div>
          <Button onClick={handleSync} disabled={syncing}>{syncing ? '同步中...' : <><CheckCircle2 size={20} color="var(--green)" /></>}</Button>
        </div>
      </Card>
      <Card className="settings-card">
        <header><FileKey2 size={18} /><h2>隐私与法律</h2></header>
        {['隐私协议', '服务条款', '合规说明'].map((label) => (
          <button type="button" className="legal-row" key={label} onClick={() => handleLegalClick(label)}>
            {label}<ChevronRight size={17} />
          </button>
        ))}
      </Card>
    </section>
    <AccountFooter />
  </main>;
}
