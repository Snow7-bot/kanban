import { useState } from 'react';
import { Eye, EyeOff, HeartPulse, LockKeyhole, UserRound } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';

export default function LoginPage({ onNavigate, onLoginSuccess }) {
  const { login, loading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    if (!account.trim() || !password.trim()) {
      setError('请输入手机号/邮箱和密码');
      return;
    }
    try {
      await login(account.trim(), password);
      if (onLoginSuccess) onLoginSuccess();
      else onNavigate('home');
    } catch (err) {
      setError(err.message || '登录失败，请重试');
    }
  };

  return <div className="auth-page login-page">
    <section className="auth-visual" style={{ backgroundImage: "url('/stitch/PRD-UI-Prototype-Implementation/login-password/assets/01.jpg')" }}>
      <div className="auth-brand"><b><HeartPulse size={24} />康伴</b><span>Medical AI</span></div>
      <div className="auth-slogan"><h1>临床级精准<br />充满关怀的智能</h1><p>您的专业医疗AI助手，旨在将高端临床见解与直观便捷的家庭应用无缝结合。</p></div>
    </section>
    <main className="auth-panel">
      <form className="login-form" onSubmit={handleSubmit}>
        <header><h1>欢迎登录</h1><p>请输入您的信息以访问您的健康数据</p></header>
        <label>手机号或邮箱<div><UserRound size={17} /><input aria-label="手机号或邮箱" placeholder="请输入手机号或邮箱" required value={account} onChange={e => { setAccount(e.target.value); setError(''); }} /></div></label>
        <div className="auth-field">
          <div className="auth-field-heading"><label htmlFor="login-password">密码</label><button type="button" onClick={() => onNavigate('password-reset')}>忘记密码？</button></div>
          <div className="auth-input-wrap"><LockKeyhole size={17} /><input id="login-password" aria-label="密码" placeholder="请输入密码" type={showPassword ? 'text' : 'password'} required value={password} onChange={e => { setPassword(e.target.value); setError(''); }} /><button className="password-visibility-toggle" type="button" aria-label="显示密码" onClick={event => { event.preventDefault(); event.stopPropagation(); setShowPassword(value => !value); }}>{showPassword ? <Eye size={17} /> : <EyeOff size={17} />}</button></div>
        </div>
        {error && <p className="auth-error">{error}</p>}
        <button className="auth-primary" type="submit" disabled={loading}>{loading ? '登录中...' : '登录'}</button>
        <p className="auth-switch">还没有账号？<button type="button" onClick={() => onNavigate('register')}>立即注册</button></p>
      </form>
      <footer>© 2024 康伴 KangBan.<br /><span>健康参考，不替代医生诊断/处方</span></footer>
    </main>
  </div>;
}
