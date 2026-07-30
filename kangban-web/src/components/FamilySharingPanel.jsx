import { useEffect, useState } from 'react';
import { Check, Clock3, ShieldCheck, UserPlus, Users, X } from 'lucide-react';
import * as familyApi from '../api/family.js';

const PERMISSIONS = [
  ['canViewHealth', '查看健康指标'],
  ['canAddHealth', '录入健康指标'],
  ['canViewRecords', '查看病历'],
  ['canViewMedications', '查看用药'],
  ['canViewReports', '查看健康报告'],
  ['canUseAi', '用于 AI 个性化分析'],
  ['canModify', '修改健康数据'],
  ['canDelete', '删除健康数据'],
];

const DEFAULT_PERMISSIONS = {
  canViewHealth: true,
  canAddHealth: false,
  canViewRecords: false,
  canViewMedications: false,
  canViewReports: true,
  canUseAi: true,
  canModify: false,
  canDelete: false,
};

function Modal({ title, children, onClose }) {
  return <div className="family-modal-backdrop" role="presentation" onMouseDown={onClose}>
    <section className="family-modal" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(event) => event.stopPropagation()}>
      <header><div><h2>{title}</h2><p>健康数据属于敏感信息，请只授予必要权限。</p></div><button type="button" aria-label="关闭" onClick={onClose}><X size={18} /></button></header>
      {children}
    </section>
  </div>;
}

function PermissionFields({ value, onChange, disabled = false }) {
  return <fieldset className="family-permission-list" disabled={disabled}>
    <legend>共享范围</legend>
    {PERMISSIONS.map(([key, label]) => <label key={key}>
      <input type="checkbox" checked={Boolean(value?.[key])} onChange={(event) => onChange?.({ ...value, [key]: event.target.checked })} />
      <span><Check size={13} />{label}</span>
    </label>)}
  </fieldset>;
}

export default function FamilySharingPanel() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [permissionTarget, setPermissionTarget] = useState(null);
  const [username, setUsername] = useState('');
  const [relation, setRelation] = useState('家人');
  const [permissions, setPermissions] = useState(DEFAULT_PERMISSIONS);

  const load = async () => {
    try {
      setError('');
      setData(await familyApi.getFamilySharing());
    } catch (err) {
      setError(err.message || '家庭共享状态加载失败');
    }
  };

  useEffect(() => { load(); }, []);

  const run = async (action, success) => {
    setBusy(true);
    try {
      await action();
      await load();
      window.dispatchEvent(new CustomEvent('app:success', { detail: success }));
      return true;
    } catch (err) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '操作失败，请重试' }));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const submitInvite = async (event) => {
    event.preventDefault();
    const ok = await run(
      () => familyApi.inviteFamilyAccount({ username, relation, permissions }),
      '邀请已发送，等待对方确认',
    );
    if (ok) {
      setInviteOpen(false);
      setUsername('');
      setPermissions(DEFAULT_PERMISSIONS);
    }
  };

  const savePermissions = async (event) => {
    event.preventDefault();
    const ok = await run(
      () => familyApi.updateFamilyPermission(permissionTarget.userId, permissionTarget.permissions),
      '共享权限已更新',
    );
    if (ok) setPermissionTarget(null);
  };

  const incoming = data?.incomingInvitations || [];
  const subjects = data?.sharedSubjects || [];
  const granted = data?.grantedAccess || [];
  const sent = data?.sentInvitations || [];

  return <section className="family-sharing-panel" aria-label="家庭账号共享">
    <div className="family-sharing-head">
      <div><span><ShieldCheck size={16} />家庭账号共享</span><p>邀请已注册家人，在对方确认后按授权范围查看健康数据。</p></div>
      <button type="button" onClick={() => setInviteOpen(true)}><UserPlus size={15} />邀请家人账号</button>
    </div>

    {error && <p className="family-sharing-error">{error}<button type="button" onClick={load}>重试</button></p>}

    {incoming.length > 0 && <div className="family-invitations">
      <h3>待处理邀请 <b>{incoming.length}</b></h3>
      {incoming.map((invite) => <article key={invite.id}>
        <i><Users size={18} /></i>
        <div><strong>{invite.name} 邀请你加入家庭</strong><small>接受后，对方只能使用下方列出的数据范围。</small><PermissionFields value={invite.permissions} disabled /></div>
        <footer><button type="button" disabled={busy} onClick={() => run(() => familyApi.rejectFamilyInvitation(invite.id), '已拒绝邀请')}>拒绝</button><button type="button" disabled={busy} onClick={() => run(() => familyApi.acceptFamilyInvitation(invite.id), '已加入家庭')}>同意</button></footer>
      </article>)}
    </div>}

    {(subjects.length > 0 || granted.length > 0 || sent.length > 0) && <div className="family-sharing-accounts">
      {subjects.map((account) => <article key={`subject-${account.userId}`}>
        <span className="family-account-avatar">{account.avatarUrl ? <img src={account.avatarUrl} alt="" /> : account.name?.[0]}</span>
        <div><strong>{account.name}</strong><small>{account.relation || '家庭成员'} · 已授权我查看</small></div>
        <span className="family-account-state"><ShieldCheck size={14} />已授权</span>
      </article>)}
      {granted.map((account) => <article key={`granted-${account.userId}`}>
        <span className="family-account-avatar">{account.avatarUrl ? <img src={account.avatarUrl} alt="" /> : account.name?.[0]}</span>
        <div><strong>{account.name}</strong><small>可以按当前权限查看我的数据</small></div>
        <button type="button" onClick={() => setPermissionTarget({ ...account, permissions: { ...account.permissions } })}>管理权限</button>
      </article>)}
      {sent.map((invite) => <article key={`sent-${invite.id}`}>
        <span className="family-account-avatar"><Clock3 size={17} /></span>
        <div><strong>{invite.name}</strong><small>邀请已发送，等待对方确认</small></div>
        <span className="family-account-state waiting">待确认</span>
      </article>)}
    </div>}

    {inviteOpen && <Modal title="邀请家人账号" onClose={() => setInviteOpen(false)}>
      <form className="family-sharing-form" onSubmit={submitInvite}>
        <label><span>家人用户名</span><input required value={username} onChange={(event) => setUsername(event.target.value)} placeholder="输入对方注册用户名" /></label>
        <label><span>与我的关系</span><select value={relation} onChange={(event) => setRelation(event.target.value)}><option>家人</option><option>父亲</option><option>母亲</option><option>配偶</option><option>子女</option><option>其他</option></select></label>
        <PermissionFields value={permissions} onChange={setPermissions} />
        <p className="family-consent-note">对方接受邀请即表示同意向你开放勾选的数据；未勾选的数据不会被读取。</p>
        <footer><button type="button" onClick={() => setInviteOpen(false)}>取消</button><button type="submit" disabled={busy}>{busy ? '发送中…' : '发送邀请'}</button></footer>
      </form>
    </Modal>}

    {permissionTarget && <Modal title={`管理 ${permissionTarget.name} 的访问权限`} onClose={() => setPermissionTarget(null)}>
      <form className="family-sharing-form" onSubmit={savePermissions}>
        <PermissionFields value={permissionTarget.permissions} onChange={(next) => setPermissionTarget({ ...permissionTarget, permissions: next })} />
        <p className="family-consent-note">保存后立即生效。取消某项权限后，对方下一次请求会被拒绝。</p>
        <footer className="family-permission-actions">
          <button type="button" className="danger" disabled={busy} onClick={async () => {
            if (!window.confirm(`确定撤销 ${permissionTarget.name} 查看你健康数据的全部权限吗？`)) return;
            const ok = await run(() => familyApi.revokeFamilyPermission(permissionTarget.userId), '授权已撤销');
            if (ok) setPermissionTarget(null);
          }}>撤销全部</button>
          <span />
          <button type="button" onClick={() => setPermissionTarget(null)}>取消</button>
          <button type="submit" disabled={busy}>保存权限</button>
        </footer>
      </form>
    </Modal>}
  </section>;
}
