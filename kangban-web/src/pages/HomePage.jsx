import { ArrowRight, Bell, CalendarDays, ChevronRight, FileHeart, HeartPulse, Bot, MoreHorizontal, Search, ShieldCheck, Syringe, Watch } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';
import * as healthApi from '../api/health.js';
import * as medicationApi from '../api/medications.js';
import { DEFAULT_AVATAR_URL, getUserDisplayName } from '../data.js';

function latestMetric(trend, unit) {
  const record = trend?.records?.[0];
  if (!record) return { value: '--', unit, note: '暂无记录' };
  const date = String(record.recordedDate || '').replaceAll('-', '/');
  const time = record.recordedTime ? ` ${String(record.recordedTime).slice(0, 5)}` : '';
  return { value: record.value || '—', unit: record.unit || unit, note: `上次检查 · ${date}${time}` };
}

function firstMedicationTime(times) {
  try {
    const values = Array.isArray(times) ? times : JSON.parse(times || '[]');
    return Array.isArray(values) && values[0] ? values[0] : '未设置时间';
  } catch {
    return String(times || '未设置时间').split(',')[0];
  }
}

function sleepText(value) {
  const hours = Number(value);
  if (!Number.isFinite(hours)) return '--';
  return `${Math.floor(hours)}h ${String(Math.round((hours % 1) * 60)).padStart(2, '0')}m`;
}

function HomeMetricCard({ type, icon: Icon, title, note, children, status, image, onMore }) {
  return <article className={`home-metric-card ${type}`}>
    <div className="home-metric-top">
      <div><div className="home-metric-title"><Icon size={15} /><strong>{title}</strong></div><small>{note}</small></div>
      <button aria-label={`${title}更多操作`} onClick={onMore}><MoreHorizontal size={16} /></button>
    </div>
    {children}
    {status && <div className="home-metric-status"><span />{status}</div>}
    {image && <img className="home-device-image" src={image} alt="Apple Watch 健康设备" />}
    <div className="home-dot-chart" aria-hidden="true" />
  </article>;
}

export default function HomePage({ onNavigate }) {
  const { user, isAuthenticated } = useAuth();
  const userName = isAuthenticated ? getUserDisplayName(user, '未设置姓名') : '访客';
  const userAvatar = user?.avatarUrl || DEFAULT_AVATAR_URL;
  const patientAge = isAuthenticated && user?.age ? `${user.age}岁` : '--';
  const patientGender = isAuthenticated ? user?.gender || '--' : '--';
  const patientBirthday = isAuthenticated ? user?.birthday || '--' : '--';
  const patientHeight = isAuthenticated && user?.height ? `${user.height}cm` : '--';
  const patientWeight = isAuthenticated && user?.weight ? `${user.weight}kg` : '--';
  const [dashboard, setDashboard] = useState({});

  useEffect(() => {
    if (!isAuthenticated) {
      setDashboard({});
      return undefined;
    }
    let active = true;
    Promise.all([
      healthApi.getHealthTrends({ metric: 'heart_rate', days: 30 }),
      healthApi.getHealthTrends({ metric: 'blood_pressure', days: 30 }),
      healthApi.getHealthTrends({ metric: 'blood_sugar', days: 30 }),
      healthApi.getHealthReport({ period: 'week' }),
      medicationApi.getMedications(),
    ]).then(([heart, pressure, glucose, report, medications]) => {
      if (active) setDashboard({ heart, pressure, glucose, report, medications });
    }).catch(() => {
      if (active) setDashboard({});
    });
    return () => { active = false; };
  }, [isAuthenticated]);

  const heart = isAuthenticated ? latestMetric(dashboard.heart, '次/分') : { value: '--', unit: '次/分', note: '登录后查看' };
  const pressure = isAuthenticated ? latestMetric(dashboard.pressure, 'mmHg') : { value: '--', unit: 'mmHg', note: '登录后查看' };
  const glucose = isAuthenticated ? latestMetric(dashboard.glucose, 'mmol/L') : { value: '--', unit: 'mmol/L', note: '登录后查看' };
  const [pressureTop = '--', pressureBottom] = String(pressure.value).split('/');
  const recordCount = dashboard.report?.recordCount || 0;
  const stepAverage = Number(dashboard.report?.steps?.average);
  const steps = Number.isFinite(stepAverage) ? stepAverage.toLocaleString() : '--';
  const medications = Array.isArray(dashboard.medications) ? dashboard.medications.slice(0, 2) : [];
  const protectedFeedback = (message) => {
    if (!isAuthenticated) {
      onNavigate('profile');
      return;
    }
    window.dispatchEvent(new CustomEvent('app:success', { detail: message }));
  };

  return <div className="stitch-home">
    <div className="home-stage">
      <main className="home-center">
        <header className="home-header">
          <div><h1>你好, {userName}</h1><p>今天感觉怎么样？</p></div>
          <div className="home-header-actions">
            <span className="home-connected"><i />{isAuthenticated ? '设备已连接' : '登录后连接设备'}</span>
            <button aria-label="搜索" onClick={() => protectedFeedback('搜索功能')}><Search size={18} /></button>
            <button className="home-notification" aria-label="通知" onClick={() => protectedFeedback('暂无新通知')}><Bell size={18} />{isAuthenticated && <i />}</button>
          </div>
        </header>
        <button className="home-ai-hero" onClick={() => onNavigate('consultation')}>
          <span><span className="home-ai-label"><i><Bot size={19} /></i>AI 智能助理</span><strong>开始智能问诊</strong><small>描述您的症状，获取初步建议或快速预约医生。</small></span>
          <i className="home-ai-arrow"><ArrowRight size={27} /></i>
        </button>
        <section className="home-metrics-section">
          <div className="home-section-title"><h2>健康指标</h2><button onClick={() => onNavigate('trends')}>查看全部 <ChevronRight size={14} /></button></div>
          <div className="home-metrics-grid">
            <HomeMetricCard type="heart" icon={HeartPulse} title="心率 (ECG)" note={heart.note} onMore={() => onNavigate('trends')}>
              <div className="home-reading"><strong>{heart.value}</strong><span>{heart.unit}</span></div>
            </HomeMetricCard>
            <HomeMetricCard type="pressure" icon={Syringe} title="血压" note={pressure.note} onMore={() => onNavigate('trends')}>
              <div className="home-reading"><strong>{pressureTop}{pressureBottom && <em>/{pressureBottom}</em>}</strong><span>{pressure.unit}</span></div>
            </HomeMetricCard>
            <HomeMetricCard type="glucose" icon={ShieldCheck} title="血糖" note={glucose.note} onMore={() => onNavigate('trends')}>
              <div className="home-reading"><strong>{glucose.value}</strong><span>{glucose.unit}</span></div>
            </HomeMetricCard>
            <HomeMetricCard type="device" icon={Watch} title="Apple Watch Series X" note={isAuthenticated ? '已连接 · 电量 80%' : '登录后查看'} image={isAuthenticated ? '/stitch-prd-ui-prototypes/assets/asset-04.jpg' : null} onMore={() => onNavigate('settings')} />
          </div>
        </section>
      </main>
      <aside className="home-right-rail">
        <section className="home-profile-panel">
          <img src={userAvatar} alt={`${userName}头像`} />
          <h2>{isAuthenticated ? `${userName} (自己)` : '未登录'}</h2>
          <p>{patientAge} · {patientGender} · {patientBirthday}</p>
          <div><span>身高: {patientHeight}</span><span>体重: {patientWeight}</span></div>
        </section>
        <section>
          <div className="home-rail-heading"><h3>用药提醒</h3><button aria-label="添加提醒" onClick={() => onNavigate('medication-add')}>+</button></div>
          <div className="home-reminder-list">
            {medications.length ? medications.map((medication, index) => <article key={medication.id}>
              <i className={index ? 'blue' : ''}><CalendarDays size={16} /></i>
              <span><strong>{medication.name}{medication.dosage ? ` ${medication.dosage}` : ''}</strong><small>{medication.frequency || '按医嘱'} · {firstMedicationTime(medication.times)}</small></span>
              <em className={medication.status === 'completed' ? 'done' : ''}>{medication.status === 'completed' ? '已完成' : '待服用'}</em>
            </article>) : <article><span><strong>{isAuthenticated ? '暂无用药提醒' : '登录后查看'}</strong><small>{isAuthenticated ? '可在用药管理中添加药物' : '登录后管理您的用药计划'}</small></span></article>}
          </div>
        </section>
        <section>
          <div className="home-rail-heading"><h3>本周健康总结</h3><button onClick={() => onNavigate('health-report')}>查看详情</button></div>
          <article className="home-report-card home-weekly-summary">
            <button className="home-weekly-copy" onClick={() => onNavigate('health-report')}>
              <div className="home-report-row"><i><b>本周</b><strong>{recordCount || '--'}</strong></i><span><strong>{!isAuthenticated ? '登录后查看' : recordCount ? `已汇总 ${recordCount} 条健康记录` : '本周暂无健康数据'}</strong><small>{isAuthenticated ? dashboard.report?.dateRange || '暂无数据' : '--'}</small></span></div>
              <div className="home-weekly-stats"><span>睡眠 <b>{sleepText(dashboard.report?.sleep?.average)}</b></span><span>步数 <b>{steps}</b></span></div>
            </button>
            <button onClick={() => onNavigate('health-report')}><FileHeart size={15} />查看完整总结</button>
          </article>
        </section>
      </aside>
    </div>
  </div>;
}
