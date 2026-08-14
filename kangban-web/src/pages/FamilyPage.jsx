import { useState, useEffect } from 'react';
import { Activity, Camera, ChevronRight, HeartPulse, Plus, Trash2, UserPlus } from 'lucide-react';
import { Button, Card, StatusChip } from '../components/UI.jsx';
import AccountFooter from '../components/AccountFooter.jsx';
import FamilySharingPanel from '../components/FamilySharingPanel.jsx';
import * as familyApi from '../api/family.js';
import { useAsync } from '../hooks/useAsync.js';
import { API_CONFIG } from '../api/config.js';

const metricLabels = {
  heart_rate: '心率',
  blood_pressure: '血压',
  blood_sugar: '血糖',
  weight: '体重',
  temperature: '体温',
  sleep: '睡眠',
  steps: '步数',
};

function getMemberHealth(member) {
  const latest = member.latestHealth;
  if (!latest) return { tone: 'gray', status: '待记录', label: '最近健康记录', value: '暂无记录' };
  return {
    tone: 'green',
    status: '已记录',
    label: metricLabels[latest.metric] || latest.metric || '最近健康记录',
    value: [latest.value, latest.unit].filter(Boolean).join(' ') || '暂无记录',
  };
}

export default function FamilyPage({ onNavigate }) {
  const [selectedId, setSelectedId] = useState(null);
  const {
    data: members,
    loading,
    error,
    empty,
    execute: fetchMembers,
    setData: setMembers,
  } = useAsync(familyApi.getFamilyMembers, { immediate: true });

  // 数据加载后自动选中第一个成员
  useEffect(() => {
    if (members && members.length > 0 && !selectedId) {
      setSelectedId(members[0].id);
    }
  }, [members, selectedId]);

  const handleDelete = async (id, e) => {
    e.stopPropagation();
    if (!window.confirm('确定要删除该家庭成员吗？')) return;
    try {
      await familyApi.deleteFamilyMember(id);
      setMembers((prev) => prev.filter((m) => m.id !== id));
      if (selectedId === id) {
        setSelectedId(null);
      }
      window.dispatchEvent(
        new CustomEvent('app:success', { detail: '成员已删除' })
      );
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent('app:error', {
          detail: err.message || '删除失败，请重试',
        })
      );
    }
  };

  const handleAvatarUpload = async (id, event) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (file.size > API_CONFIG.UPLOAD_MAX_SIZE) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: '头像文件不能超过 10MB' }));
      return;
    }
    try {
      const result = await familyApi.uploadFamilyAvatar(id, file);
      if (result?.url) {
        setMembers((current) => current.map((member) => (
          member.id === id ? { ...member, avatarUrl: result.url } : member
        )));
      }
      window.dispatchEvent(new CustomEvent('app:success', { detail: '成员头像已更新' }));
    } catch (err) {
      window.dispatchEvent(new CustomEvent('app:error', {
        detail: err.message || '头像上传失败，请重试',
      }));
    }
  };

  const selectedMember =
    members && members.length > 0
      ? members.find((m) => m.id === selectedId) || members[0]
      : null;

  // 加载状态
  if (loading) {
    return (
      <main className="page-content account-page family-page">
        <header className="account-heading">
          <div>
            <h1>家庭成员</h1>
            <p>统一管理您家人的健康档案与监测数据。</p>
          </div>
          <Button variant="primary" disabled>
            <UserPlus size={16} />
            添加成员
          </Button>
        </header>
        <FamilySharingPanel />
        <section className="family-grid">
          {[1, 2, 3].map((i) => (
            <div key={i} className="family-member-card is-loading">
              <div className="family-member-head">
                <div className="skeleton avatar" />
                <span>
                  <strong className="skeleton skeleton-text" />
                  <small className="skeleton skeleton-text short" />
                </span>
              </div>
              <div className="family-reading skeleton">
                <span className="skeleton skeleton-text short" />
                <strong className="skeleton skeleton-text" />
              </div>
              <p className="skeleton skeleton-text" />
              <footer className="skeleton skeleton-text short" />
            </div>
          ))}
        </section>
        <Card className="family-insight">
          <div>
            <span className="family-insight-kicker">
              <HeartPulse size={15} />
              家庭健康脉搏
            </span>
            <h2>守护家人的每一次健康变化</h2>
            <p>每项健康指标都在持续更新，帮助您及时了解全家人的健康动态。</p>
            <div className="family-insight-stats">
              <span>
                <b>健康成员</b>
                <strong>--</strong>
              </span>
              <span>
                <b>待处理提醒</b>
                <strong>--</strong>
              </span>
            </div>
          </div>
          <div className="family-chart" aria-label="家庭健康趋势图">
            <Activity size={19} />
            <div>
              {[42, 62, 47, 85, 54, 69].map((height, index) => (
                <i key={index} style={{ height: `${height}%` }} />
              ))}
            </div>
          </div>
        </Card>
        <AccountFooter />
      </main>
    );
  }

  // 错误状态
  if (error) {
    return (
      <main className="page-content account-page family-page">
        <header className="account-heading">
          <div>
            <h1>家庭成员</h1>
            <p>统一管理您家人的健康档案与监测数据。</p>
          </div>
        </header>
        <FamilySharingPanel />
        <section className="family-grid">
          <div className="family-error-card">
            <p>数据加载失败：{error}</p>
            <Button variant="primary" onClick={() => fetchMembers()}>
              重试
            </Button>
          </div>
        </section>
        <AccountFooter />
      </main>
    );
  }

  // 空状态
  if (empty) {
    return (
      <main className="page-content account-page family-page">
        <header className="account-heading">
          <div>
            <h1>家庭成员</h1>
            <p>统一管理您家人的健康档案与监测数据。</p>
          </div>
          <Button
            variant="primary"
            onClick={() => onNavigate('family-add')}
          >
            <UserPlus size={16} />
            添加成员
          </Button>
        </header>
        <FamilySharingPanel />
        <section className="family-grid">
          <div className="family-empty-card">
            <i>
              <UserPlus size={32} />
            </i>
            <strong>暂无家庭成员</strong>
            <p>添加家庭成员，建立健康档案</p>
            <Button
              variant="primary"
              onClick={() => onNavigate('family-add')}
            >
              <UserPlus size={16} />
              添加成员
            </Button>
          </div>
          <button
            type="button"
            className="family-add-card"
            onClick={() => onNavigate('family-add')}
          >
            <i>
              <Plus size={23} />
            </i>
            <strong>添加新成员</strong>
            <span>为家庭成员建立健康档案</span>
          </button>
        </section>
        <Card className="family-insight">
          <div>
            <span className="family-insight-kicker">
              <HeartPulse size={15} />
              家庭健康脉搏
            </span>
            <h2>守护家人的每一次健康变化</h2>
            <p>每项健康指标都在持续更新，帮助您及时了解全家人的健康动态。</p>
            <div className="family-insight-stats">
              <span>
                <b>健康成员</b>
                <strong>--</strong>
              </span>
              <span>
                <b>待处理提醒</b>
                <strong>--</strong>
              </span>
            </div>
          </div>
          <div className="family-chart" aria-label="家庭健康趋势图">
            <Activity size={19} />
            <div>
              {[42, 62, 47, 85, 54, 69].map((height, index) => (
                <i key={index} style={{ height: `${height}%` }} />
              ))}
            </div>
          </div>
        </Card>
        <AccountFooter />
      </main>
    );
  }

  // 正常渲染
  const healthyCount = members.filter((member) => member.latestHealth).length;
  const reminderCount = 0;

  return (
    <main className="page-content account-page family-page">
      <header className="account-heading">
        <div>
          <h1>家庭成员</h1>
          <p>统一管理您家人的健康档案与监测数据。</p>
        </div>
        <Button
          variant="primary"
          onClick={() => onNavigate('family-add')}
        >
          <UserPlus size={16} />
          添加成员
        </Button>
      </header>
      <FamilySharingPanel />

      <section className="family-grid">
        {members.map((member) => {
          const health = getMemberHealth(member);
          return <article
            key={member.id}
            className={`family-member-card ${selectedId === member.id ? 'selected' : ''}`}
          >
            <button
              type="button"
              className="family-member-select"
              aria-pressed={selectedId === member.id}
              onClick={() => setSelectedId(member.id)}
            >
              <div className="family-member-head">
                {member.avatarUrl
                  ? <img src={member.avatarUrl} alt={`${member.name}头像`} />
                  : <i className="family-avatar-fallback" aria-label={`${member.name}默认头像`}>{member.name?.[0] || '家'}</i>}
                <span>
                  <strong>{member.name}</strong>
                  <small>{member.relation}</small>
                </span>
                <StatusChip tone={health.tone}>
                  {health.status}
                </StatusChip>
              </div>
              <div className={`family-reading ${health.tone}`}>
                <span>{health.label}</span>
                <strong>{health.value}</strong>
              </div>
              <p>{member.note}</p>
              <span className="family-profile-link">查看档案 <ChevronRight size={15} /></span>
            </button>
            <button
              type="button"
              className="family-delete-btn"
              aria-label={`删除${member.name}`}
              onClick={(e) => handleDelete(member.id, e)}
            >
              <Trash2 size={14} />
            </button>
            <label className="family-avatar-upload" aria-label={`更新${member.name}头像`}>
              <Camera size={13} />
              <input type="file" accept="image/*" onChange={(event) => handleAvatarUpload(member.id, event)} />
            </label>
          </article>;
        })}
        <button
          type="button"
          className="family-add-card"
          onClick={() => onNavigate('family-add')}
        >
          <i>
            <Plus size={23} />
          </i>
          <strong>添加新成员</strong>
          <span>为家庭成员建立健康档案</span>
        </button>
      </section>

      <Card className="family-insight">
        <div>
          <span className="family-insight-kicker">
            <HeartPulse size={15} />
            家庭健康脉搏
          </span>
          <h2>守护家人的每一次健康变化</h2>
          <p>
            每项健康指标都在持续更新，帮助您及时了解全家人的健康动态。
          </p>
          <div className="family-insight-stats">
            <span>
              <b>健康成员</b>
              <strong>{String(healthyCount).padStart(2, '0')}</strong>
            </span>
            <span>
              <b>待处理提醒</b>
              <strong>{String(reminderCount).padStart(2, '0')}</strong>
            </span>
          </div>
        </div>
        <div className="family-chart" aria-label="家庭健康趋势图">
          <Activity size={19} />
          <div><small>暂无聚合趋势数据</small></div>
        </div>
      </Card>

      <AccountFooter />
    </main>
  );
}
