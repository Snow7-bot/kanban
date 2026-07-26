import { useState, useEffect } from 'react';
import { Bell, CalendarDays, ChevronDown, Footprints, HeartPulse, MoonStar, Sparkles, UserRound, UsersRound } from 'lucide-react';
import * as healthApi from '../api/health.js';
import * as familyApi from '../api/family.js';

function SummaryBars({ values, tone = 'red' }) {
  if (!values.length) return <div className={`report-bars ${tone}`}>暂无趋势数据</div>;
  return <div className={`report-bars ${tone}`}>{values.map((height, index) => <i key={`${height}-${index}`} style={{ height: `${height}%` }} />)}</div>;
}

function sleepDuration(value) {
  const hours = Number(value);
  if (!Number.isFinite(hours)) return { hours: '—', minutes: '' };
  return { hours: Math.floor(hours), minutes: String(Math.round((hours % 1) * 60)).padStart(2, '0') };
}

function chartBars(records) {
  const values = (records || []).map(record => Number(record.value)).filter(Number.isFinite).reverse();
  if (!values.length) return [];
  const min = Math.min(...values);
  const max = Math.max(...values);
  return values.map(value => max === min ? 55 : 20 + ((value - min) / (max - min)) * 80);
}

export default function HealthReportPage() {
  const [period, setPeriod] = useState('周');
  const [memberOpen, setMemberOpen] = useState(false);
  const [selectedKey, setSelectedKey] = useState('self');
  const [targets, setTargets] = useState([]);
  const [report, setReport] = useState(null);
  const [trends, setTrends] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const isWeek = period === '周';
  const selectedTarget = targets.find((item) => item.key === selectedKey) || null;
  const memberId = selectedTarget?.memberId ?? null;
  const subjectUserId = selectedTarget?.subjectUserId ?? null;
  const memberName = selectedTarget?.name || '自己';

  useEffect(() => {
    familyApi.getPatientTargets()
      .then((items) => setTargets(items.filter((item) => item.kind !== 'account' || item.permissions?.canViewReports)))
      .catch(() => setTargets([]));
  }, []);

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const days = isWeek ? 7 : 30;
        const [data, heart, steps] = await Promise.all([
          healthApi.getHealthReport({ period: isWeek ? 'week' : 'month', memberId, subjectUserId }),
          healthApi.getHealthTrends({ metric: 'heart_rate', days, memberId, subjectUserId }),
          healthApi.getHealthTrends({ metric: 'steps', days, memberId, subjectUserId }),
        ]);
        setReport(data);
        setTrends({ heart, steps });
      } catch (err) {
        setError(err.message || '加载报告失败');
      } finally {
        setLoading(false);
      }
    })();
  }, [period, memberId, subjectUserId]);

  if (loading) return <main className="page-content"><div className="state-loading"><div className="state-spinner" /><p>加载中...</p></div></main>;
  if (error) return <main className="page-content"><div className="state-error"><p>{error}</p></div></main>;

  const reportDate = report?.dateRange || '暂无数据';
  const heartValue = report?.heartRate?.average || '—';
  const stepAverage = Number(report?.steps?.average);
  const stepValue = Number.isFinite(stepAverage) ? stepAverage.toLocaleString() : '—';
  const sleep = sleepDuration(report?.sleep?.average);
  const insight = report?.insight || '暂无健康数据。';
  const heartBars = chartBars(trends.heart?.records);
  const stepBars = chartBars(trends.steps?.records);

  return <main className="health-report">
    <header className="report-header">
      <div><h1>本{period}健康总结</h1><p>{reportDate}</p></div>
      <div className="report-controls">
        <div className="report-member">
          <button aria-expanded={memberOpen} onClick={() => setMemberOpen(!memberOpen)}>
            <img src="/stitch/PRD-UI-Prototype-Implementation/health-report/assets/01.jpg" alt="头像" /><span>{memberName}</span><ChevronDown size={15} />
          </button>
          {memberOpen && <div className="report-member-menu">
            <button onClick={() => { setSelectedKey('self'); setMemberOpen(false); }}><UserRound size={14} />自己</button>
            {targets.map((item) => <button key={item.key} onClick={() => { setSelectedKey(item.key); setMemberOpen(false); }}><UsersRound size={14} />{item.name}{item.kind === 'account' ? '（共享）' : ''}</button>)}
          </div>}
        </div>
        <div className="report-period" role="group" aria-label="报告周期">
          {['周', '月'].map(item => <button key={item} className={period === item ? 'active' : ''} onClick={() => setPeriod(item)}>{item}</button>)}
        </div>
        <button className="report-notice" aria-label="通知" onClick={() => window.dispatchEvent(new CustomEvent('app:success', { detail: '暂无新通知' }))}><Bell size={16} /></button>
      </div>
    </header>
    <section className="report-dashboard">
      <article className="report-insight"><i><Sparkles size={22} fill="currentColor" /></i><div><h2>AI 健康洞察</h2><p>{insight}</p></div></article>
      <article className="report-card report-heart">
        <div className="report-card-head"><div><div className="report-label"><HeartPulse size={16} />心率趋势</div><strong>{heartValue}<small>bpm (均值)</small></strong></div><span>正常范围</span></div>
        <SummaryBars values={heartBars} />
      </article>
      <article className="report-card report-sleep">
        <div className="report-label"><MoonStar size={16} />睡眠质量</div>
        <div className="report-sleep-ring">
          <svg viewBox="0 0 100 100" aria-label={`睡眠时长 ${sleep.hours}${sleep.minutes ? `小时${sleep.minutes}分钟` : ''}`}><circle cx="50" cy="50" r="40" /><circle className="report-sleep-progress" cx="50" cy="50" r="40" /></svg>
          <div><strong>{sleep.hours}{sleep.hours === '—' ? '' : 'h'}</strong><span>{sleep.minutes && `${sleep.minutes}m`}</span></div>
        </div>
        <p>{report?.sleep?.average ? `平均睡眠 ${report.sleep.average} 小时` : '暂无睡眠记录'}<br />{report?.recordCount ? `已汇总 ${report.recordCount} 条记录` : '请先录入健康指标'}</p>
      </article>
      <article className="report-card report-steps">
        <div className="report-card-head"><div><div className="report-label"><Footprints size={16} />运动步数</div><strong>{stepValue}<small>步/日</small></strong></div></div>
        <SummaryBars values={stepBars} tone="blue" />
      </article>
      <article className="report-appointment"><i><CalendarDays size={18} /></i><div><h2>下次体检提醒</h2><p>{report?.nextAppointment || '暂未设置体检提醒'}</p></div><button onClick={() => window.dispatchEvent(new CustomEvent('app:success', { detail: '预约详情已查看' }))}>查看预约详情</button></article>
    </section>
    <footer className="report-footer">健康参考，不替代医生诊断/处方</footer>
  </main>;
}
