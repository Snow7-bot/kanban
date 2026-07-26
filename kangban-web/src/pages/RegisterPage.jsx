import { useState } from 'react';
import { Eye, EyeOff, HeartPulse, LockKeyhole, Phone, ShieldCheck } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import * as authApi from '../api/auth.js';

export default function RegisterPage({ onNavigate }) {
  const { register, loading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSendCode = async () => {
    if (!phone.trim()) {
      setError('请输入手机号码');
      return;
    }
    try {
      await authApi.sendVerifyCode(phone.trim());
      setCodeSent(true);
      setError('');
      window.dispatchEvent(new CustomEvent('app:success', { detail: '验证码已发送' }));
    } catch (err) {
      setError(err.message || '发送验证码失败');
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    if (!agreed) {
      setError('请阅读并同意用户服务协议和隐私政策');
      return;
    }
    if (!phone.trim() || !code.trim() || !password.trim()) {
      setError('请填写所有必填项');
      return;
    }
    try {
      const result = await register({ phone: phone.trim(), code: code.trim(), password });
      setSuccess(true);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '注册成功，请登录' }));
      setTimeout(() => onNavigate('login'), 1500);
    } catch (err) {
      setError(err.message || '注册失败');
    }
  };

  return <div className="auth-page register-page">
    <section className="auth-visual" style={{ backgroundImage: "url('/stitch/PRD-UI-Prototype-Implementation/register/assets/01.jpg')" }} />
    <main className="auth-panel register-panel">
      <form className="register-form" onSubmit={handleSubmit}>
        <header><i><HeartPulse size={30} /></i><h1>创建康伴账号</h1><p>开启您的专属智能健康管理</p></header>
        <label>手机号码<div><Phone size={17} /><input aria-label="手机号码" placeholder="请输入您的手机号" required value={phone} onChange={e => { setPhone(e.target.value); setError(''); }} /></div></label>
        <label>验证码<div className="code-row"><span><ShieldCheck size={17} /><input aria-label="验证码" placeholder="输入验证码" required value={code} onChange={e => { setCode(e.target.value); setError(''); }} /></span><button type="button" onClick={handleSendCode} disabled={codeSent}>{codeSent ? '已发送' : '获取验证码'}</button></div></label>
        <label>设置密码<div><LockKeyhole size={17} /><input aria-label="设置密码" placeholder="设置6-20位密码" type={showPassword ? 'text' : 'password'} required value={password} onChange={e => { setPassword(e.target.value); setError(''); }} /><button className="password-visibility-toggle" type="button" aria-label="显示密码" onClick={() => setShowPassword(!showPassword)}>{showPassword ? <Eye size={17} /> : <EyeOff size={17} />}</button></div></label>
        <label className="agreement"><input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />我已阅读并同意 <button type="button" onClick={() => window.dispatchEvent(new CustomEvent('app:success', { detail: '用户服务协议页面' }))}>《用户服务协议》</button> 和 <button type="button" onClick={() => window.dispatchEvent(new CustomEvent('app:success', { detail: '隐私政策页面' }))}>《隐私政策》</button></label>
        {error && <p className="auth-error">{error}</p>}
        <button className="auth-primary" type="submit" disabled={loading || success}>{success ? '注册成功' : loading ? '注册中...' : '注册'}</button>
        {success && <p className="auth-success">账号创建成功，即将跳转登录。</p>}
        <p className="auth-switch">已有账号？<button type="button" onClick={() => onNavigate('login')}>返回登录</button></p>
      </form>
    </main>
  </div>;
}
