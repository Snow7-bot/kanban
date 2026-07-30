import { useCallback, useEffect, useState } from 'react';
import { Eye, EyeOff, HeartPulse, LockKeyhole, Phone, RefreshCw, ShieldCheck, UserRound } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import * as authApi from '../api/auth.js';

export default function RegisterPage({ onNavigate }) {
  const { register, loading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [agreed, setAgreed] = useState(false);
  const [username, setUsername] = useState('');
  const [phone, setPhone] = useState('');
  const [captcha, setCaptcha] = useState(null);
  const [captchaAnswer, setCaptchaAnswer] = useState('');
  const [captchaLoading, setCaptchaLoading] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const loadCaptcha = useCallback(async () => {
    setCaptchaLoading(true);
    try {
      const result = await authApi.getCaptcha();
      setCaptcha(result);
      setCaptchaAnswer('');
      setError('');
    } catch (err) {
      setCaptcha(null);
      setError(err.message || '人机验证加载失败');
    } finally {
      setCaptchaLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCaptcha();
  }, [loadCaptcha]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    if (!agreed) {
      setError('请阅读并同意用户服务协议和隐私政策');
      return;
    }
    if (!username.trim() || !password.trim() || !captcha?.captchaId || !captchaAnswer.trim()) {
      setError('请填写所有必填项');
      return;
    }
    try {
      await register({
        username: username.trim(),
        phone: phone.trim() || null,
        password,
        captchaId: captcha.captchaId,
        captchaAnswer: captchaAnswer.trim(),
      });
      setSuccess(true);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '注册成功，请登录' }));
      setTimeout(() => onNavigate('login'), 1500);
    } catch (err) {
      setError(err.message || '注册失败');
      loadCaptcha();
    }
  };

  return <div className="auth-page register-page">
    <section className="auth-visual" style={{ backgroundImage: "url('/stitch/PRD-UI-Prototype-Implementation/register/assets/01.jpg')" }} />
    <main className="auth-panel register-panel">
      <form className="register-form" onSubmit={handleSubmit}>
        <header><i><HeartPulse size={30} /></i><h1>创建康伴账号</h1><p>开启您的专属智能健康管理</p></header>
        <label>用户名<div><UserRound size={17} /><input aria-label="用户名" placeholder="4-20位字母、数字、中文或下划线" required value={username} onChange={e => { setUsername(e.target.value); setError(''); }} /></div></label>
        <label>手机号码（选填）<div><Phone size={17} /><input aria-label="手机号码（选填）" placeholder="当前不作为已验证身份" value={phone} onChange={e => { setPhone(e.target.value); setError(''); }} /></div></label>
        <label>人机验证
          <div className="captcha-row">
            <span><ShieldCheck size={17} /><input aria-label="人机验证码" placeholder="输入图中5位字符" required maxLength={5} autoComplete="off" value={captchaAnswer} onChange={e => { setCaptchaAnswer(e.target.value.toUpperCase()); setError(''); }} /></span>
            <button className="captcha-image" type="button" onClick={loadCaptcha} disabled={captchaLoading} aria-label="刷新人机验证码">
              {captchaLoading || !captcha?.imageData
                ? <RefreshCw size={20} className={captchaLoading ? 'spin' : ''} />
                : <img src={captcha.imageData} alt="人机验证码，点击刷新" />}
            </button>
          </div>
        </label>
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
