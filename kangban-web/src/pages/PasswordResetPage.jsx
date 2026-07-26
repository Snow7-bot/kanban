import { useState } from 'react';
import { Eye, EyeOff, HeartPulse, LockKeyhole, Phone, ShieldCheck } from 'lucide-react';
import * as authApi from '../api/auth.js';

export default function PasswordResetPage({ onNavigate }) {
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const sendCode = async () => {
    if (!phone.trim()) return setError('请输入手机号码');
    try {
      await authApi.forgotPassword(phone.trim());
      setCodeSent(true);
      setError('');
      window.dispatchEvent(new CustomEvent('app:success', { detail: '验证码已发送' }));
    } catch (err) {
      setError(err.message || '发送验证码失败');
    }
  };

  const resetPassword = async (event) => {
    event.preventDefault();
    setError('');
    if (!phone.trim() || !code.trim() || !password.trim()) {
      setError('请填写所有必填项');
      return;
    }
    try {
      await authApi.resetPassword({ phone: phone.trim(), code: code.trim(), password });
      setSuccess(true);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '密码已重置，请重新登录' }));
      setTimeout(() => onNavigate('login'), 1200);
    } catch (err) {
      setError(err.message || '重置密码失败');
    }
  };

  return <div className="auth-page register-page">
    <section className="auth-visual" style={{ backgroundImage: "url('/stitch/PRD-UI-Prototype-Implementation/register/assets/01.jpg')" }} />
    <main className="auth-panel register-panel">
      <form className="register-form" onSubmit={resetPassword}>
        <header><i><HeartPulse size={30} /></i><h1>重置密码</h1><p>验证手机号码后设置新的登录密码</p></header>
        <label>手机号码<div><Phone size={17} /><input aria-label="手机号码" placeholder="请输入您的手机号" required value={phone} onChange={e => { setPhone(e.target.value); setError(''); }} /></div></label>
        <label>验证码<div className="code-row"><span><ShieldCheck size={17} /><input aria-label="验证码" placeholder="输入验证码" required value={code} onChange={e => { setCode(e.target.value); setError(''); }} /></span><button type="button" onClick={sendCode} disabled={codeSent}>{codeSent ? '已发送' : '获取验证码'}</button></div></label>
        <label>新密码<div><LockKeyhole size={17} /><input aria-label="新密码" placeholder="设置6-20位密码" type={showPassword ? 'text' : 'password'} required value={password} onChange={e => { setPassword(e.target.value); setError(''); }} /><button className="password-visibility-toggle" type="button" aria-label="显示密码" onClick={() => setShowPassword(!showPassword)}>{showPassword ? <Eye size={17} /> : <EyeOff size={17} />}</button></div></label>
        {error && <p className="auth-error">{error}</p>}
        <button className="auth-primary" type="submit" disabled={success}>{success ? '重置成功' : '重置密码'}</button>
        <p className="auth-switch">想起密码了？<button type="button" onClick={() => onNavigate('login')}>返回登录</button></p>
      </form>
    </main>
  </div>;
}
