import { useState, useEffect } from 'react';
import { BellRing, Check, HeartHandshake, LockKeyhole, Mail, Phone, Save, ShieldCheck, UserRound, Camera } from 'lucide-react';
import { Button, Card } from '../components/UI.jsx';
import AccountFooter from '../components/AccountFooter.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import * as userApi from '../api/user.js';
import { API_CONFIG } from '../api/config.js';
import { DEFAULT_AVATAR_URL } from '../data.js';

function normalizeProfile(data = {}) {
  return {
    name: data.name ?? '',
    gender: data.gender ?? '男',
    birthday: data.birthday ?? '',
    blood: data.blood ?? data.bloodType ?? 'O+',
    height: data.height ?? '',
    weight: data.weight ?? '',
    email: data.email ?? '',
    phone: data.phone ?? '',
    emergency: data.emergency ?? data.emergencyContact ?? '',
    avatarUrl: data.avatarUrl ?? data.avatar ?? '',
  };
}

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [saved, setSaved] = useState(false);
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState('');
  const [profile, setProfile] = useState(() => normalizeProfile(user));
  const [persistedProfile, setPersistedProfile] = useState(() => normalizeProfile(user));

  useEffect(() => {
    (async () => {
      try {
        const data = await userApi.getProfile();
        if (data) {
          const nextProfile = normalizeProfile(data);
          setProfile(nextProfile);
          setPersistedProfile(nextProfile);
          updateUser(data);
        }
      } catch (err) {
        setError(err.message || '加载个人资料失败');
      } finally {
        setFetching(false);
      }
    })();
  }, [updateUser]);

  const update = (field) => (event) => {
    setSaved(false);
    setError('');
    setProfile({ ...profile, [field]: event.target.value });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      const profilePatch = {
        name: profile.name,
        gender: profile.gender,
        birthday: profile.birthday,
        bloodType: profile.blood,
        height: profile.height === '' ? null : Number(profile.height),
        weight: profile.weight === '' ? null : Number(profile.weight),
      };
      const savedProfile = await userApi.updateProfile(profilePatch);
      if (savedProfile) {
        const nextProfile = normalizeProfile(savedProfile);
        setProfile(nextProfile);
        setPersistedProfile(nextProfile);
        updateUser(savedProfile);
      } else {
        setPersistedProfile(profile);
        updateUser(profilePatch);
      }
      setSaved(true);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '个人资料已保存' }));
    } catch (err) {
      setError(err.message || '保存失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAvatarUpload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (file.size > API_CONFIG.UPLOAD_MAX_SIZE) {
      setError('头像文件不能超过 10MB');
      return;
    }
    try {
      const result = await userApi.uploadAvatar(file);
      if (result?.url) {
        const nextProfile = { ...profile, avatarUrl: result.url };
        setProfile(nextProfile);
        setPersistedProfile(nextProfile);
        updateUser({ avatarUrl: result.url });
        setSaved(true);
        window.dispatchEvent(new CustomEvent('app:success', { detail: '头像已更新' }));
      }
    } catch (err) {
      setError(err.message || '头像上传失败');
    }
  };

  if (fetching) return <main className="page-content"><div className="state-loading"><div className="state-spinner" /><p>加载中...</p></div></main>;

  const avatarSrc = profile.avatarUrl || user?.avatarUrl || DEFAULT_AVATAR_URL;
  const patientId = user?.patientId || user?.id || 'ART-99420';

  return <main className="page-content account-page profile-page">
    <header className="account-heading profile-heading">
      <div><h1>个人资料</h1><p>管理您的健康身份。准确的信息可确保精确的临床护理和个性化的健康建议。</p></div>
      <span className="verified-pill"><ShieldCheck size={15} />已验证患者档案</span>
    </header>
    {error && <p className="auth-error" style={{ marginBottom: 12 }}>{error}</p>}
    <form className="profile-form" onSubmit={handleSubmit}>
      <Card className="profile-id-card">
        <div className="profile-avatar-wrap">
          <img src={avatarSrc} alt={`${profile.name}头像`} />
          <label className="profile-avatar-change" aria-label="更换头像">
            <Camera size={15} />
            <span>更换头像</span>
            <input type="file" accept="image/*" onChange={handleAvatarUpload} />
          </label>
        </div>
        <h2>{profile.name || '用户'}</h2>
        <p>患者 ID：{patientId}</p>
        <dl>
          <div><dt>账号状态</dt><dd>已验证</dd></div>
          <div><dt>最后更新</dt><dd>{new Date().toLocaleDateString('zh-CN')}</dd></div>
        </dl>
      </Card>
      <Card className="profile-main-card">
        <header><span><UserRound size={16} />核心信息</span></header>
        <div className="profile-fields">
          <label>姓名<input value={profile.name} onChange={update('name')} /></label>
          <label>性别<select value={profile.gender} onChange={update('gender')}><option>男</option><option>女</option><option>其他</option></select></label>
          <label>出生日期<input type="date" value={profile.birthday} onChange={update('birthday')} /></label>
          <label>血型<select value={profile.blood} onChange={update('blood')}><option>O+</option><option>A+</option><option>B+</option><option>AB+</option></select></label>
          <label>身高（cm）<input type="number" min="50" max="250" step="0.1" value={profile.height} onChange={update('height')} /></label>
          <label>体重（kg）<input type="number" min="10" max="500" step="0.1" value={profile.weight} onChange={update('weight')} /></label>
        </div>
      </Card>
      <Card className="profile-contact-card">
        <header><span><HeartHandshake size={16} />联系方式</span></header>
        <div className="contact-grid">
          <label><Mail size={14} />电子邮箱<input value={profile.email || ''} readOnly /></label>
          <label><Phone size={14} />手机号码<input value={profile.phone || ''} readOnly /></label>
          <label><ShieldCheck size={14} />紧急联系人<input value={profile.emergency || ''} placeholder="未设置" readOnly /></label>
        </div>
        <footer>
          <span><LockKeyhole size={15} />您的数据已加密并符合 HIPAA 标准。</span>
          <div>
            <Button type="button" onClick={() => {
              setProfile(persistedProfile);
              setSaved(false);
              setError('');
            }}>放弃修改</Button>
            <Button type="submit" variant="primary" disabled={loading}>
              <Save size={15} />{loading ? '保存中...' : saved ? '已保存' : '保存资料'}
            </Button>
          </div>
        </footer>
      </Card>
    </form>
    <section className="profile-shortcuts">
      <Card><i><BellRing size={20} /></i><span><strong>通知设置</strong><small>管理提醒和健康提示方式。</small></span></Card>
      <Card><i><LockKeyhole size={20} /></i><span><strong>隐私与安全</strong><small>控制数据共享与安全设置。</small></span><Check size={17} /></Card>
    </section>
    <AccountFooter />
  </main>;
}
