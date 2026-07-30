import { HeartPulse, Headphones, Phone } from 'lucide-react';

export default function PasswordResetPage({ onNavigate }) {
  return <div className="auth-page register-page">
    <section className="auth-visual" style={{ backgroundImage: "url('/stitch/PRD-UI-Prototype-Implementation/register/assets/01.jpg')" }} />
    <main className="auth-panel register-panel">
      <section className="register-form password-help-card">
        <header><i><HeartPulse size={30} /></i><h1>找回账号密码</h1><p>为保护健康隐私，暂不支持仅凭手机号自助重置密码</p></header>
        <div className="password-help-contact">
          <Headphones size={24} />
          <div><span>请联系管理员</span><a href="tel:13602060910"><Phone size={16} />13602060910</a></div>
        </div>
        <p className="password-help-note">管理员核验账号信息后会协助处理。请勿向任何人提供当前密码或登录令牌。</p>
        <p className="auth-switch">想起密码了？<button type="button" onClick={() => onNavigate('login')}>返回登录</button></p>
      </section>
    </main>
  </div>;
}
