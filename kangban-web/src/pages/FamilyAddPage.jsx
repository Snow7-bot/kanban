import { useEffect, useState } from 'react';
import { ArrowLeft, Camera, CheckCircle2, ChevronDown } from 'lucide-react';
import * as familyApi from '../api/family.js';
import { API_CONFIG } from '../api/config.js';

export default function FamilyAddPage({ onNavigate }) {
  const [saved, setSaved] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [avatarFile, setAvatarFile] = useState(null);
  const [avatarPreview, setAvatarPreview] = useState('');
  const [form, setForm] = useState({
    name: '',
    relation: '',
    age: '',
    gender: '男',
    note: '',
  });

  useEffect(() => () => {
    if (avatarPreview) URL.revokeObjectURL(avatarPreview);
  }, [avatarPreview]);

  const update = (field) => (e) => {
    setError('');
    setSaved(false);
    setForm({ ...form, [field]: e.target.value });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.name.trim()) {
      setError('请输入姓名');
      return;
    }
    setLoading(true);
    setError('');
    try {
      let avatarUploadError = null;
      const member = await familyApi.addFamilyMember({
        ...form,
        age: form.age === '' ? null : Number(form.age),
      });
      if (avatarFile && member?.id) {
        try {
          await familyApi.uploadFamilyAvatar(member.id, avatarFile);
        } catch (avatarError) {
          avatarUploadError = avatarError.message || '成员已保存，但头像上传失败';
        }
      }
      setSaved(true);
      window.dispatchEvent(new CustomEvent(avatarUploadError ? 'app:error' : 'app:success', {
        detail: avatarUploadError || '家庭成员已添加',
      }));
      setTimeout(() => onNavigate('family'), 1200);
    } catch (err) {
      setError(err.message || '保存失败');
    } finally {
      setLoading(false);
    }
  };

  const handleAvatarChange = (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (file.size > API_CONFIG.UPLOAD_MAX_SIZE) {
      setError('头像文件不能超过 10MB');
      return;
    }
    if (avatarPreview) URL.revokeObjectURL(avatarPreview);
    setAvatarFile(file);
    setAvatarPreview(URL.createObjectURL(file));
  };

  return <main className="page-content stitch-form-page family-add-page">
    <header className="family-add-heading">
      <button type="button" aria-label="返回家庭成员" onClick={() => onNavigate('family')}><ArrowLeft size={17} /></button>
      <h1>添加家庭成员</h1>
    </header>
    <form className="family-add-card-stitch" onSubmit={handleSubmit}>
      <section className="family-avatar-field">
        <label className="family-avatar-picker" aria-label="添加头像">
          {avatarPreview ? <img src={avatarPreview} alt="家庭成员头像预览" /> : <Camera size={24} />}
          <input type="file" accept="image/*" onChange={handleAvatarChange} />
        </label>
        <p>清晰的正面照片有助于您更快了解<br />这位家人的健康信息。</p>
      </section>
      {error && <p className="auth-error" style={{ textAlign: 'center', marginBottom: 8 }}>{error}</p>}
      <section className="family-add-fields">
        <label><span>姓名</span><input aria-label="姓名" placeholder="输入真实姓名" required value={form.name} onChange={update('name')} /></label>
        <label><span>关系</span><div className="select-like"><select aria-label="关系" value={form.relation} onChange={update('relation')}><option value="" disabled>选择关系</option><option>父亲</option><option>母亲</option><option>子女</option><option>配偶</option></select><ChevronDown size={15} /></div></label>
        <label><span>年龄</span><input aria-label="年龄" placeholder="例如：65" inputMode="numeric" value={form.age} onChange={update('age')} /></label>
        <fieldset><legend>性别</legend>
          <label><input type="radio" name="gender" value="男" checked={form.gender === '男'} onChange={update('gender')} />男</label>
          <label><input type="radio" name="gender" value="女" checked={form.gender === '女'} onChange={update('gender')} />女</label>
        </fieldset>
        <label className="family-note"><span>健康备注（选填）</span><textarea aria-label="健康备注" placeholder="例如：高血压、糖尿病、对青霉素过敏等重要医疗信息..." value={form.note} onChange={update('note')} /></label>
      </section>
      <footer>
        <button type="button" onClick={() => onNavigate('family')}>取消</button>
        <button type="submit" disabled={loading}>{saved ? <><CheckCircle2 size={15} />已保存</> : loading ? '保存中...' : '保存成员'}</button>
      </footer>
      {saved && <p className="form-success family-success">家庭成员已保存，可继续补充健康档案。</p>}
    </form>
  </main>;
}
