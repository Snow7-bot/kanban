import { useState, useEffect, useCallback } from 'react';
import {
  Activity, AlertTriangle, CheckCircle2, ChevronRight, Download,
  Filter, HeartPulse, Minus, MoreHorizontal, Plus, Users,
} from 'lucide-react';
import HealthChart from '../components/HealthChart.jsx';
import * as healthApi from '../api/health.js';
import { useAsync } from '../hooks/useAsync.js';
import { StateBoundary } from '../hooks/StateBoundary.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { DEFAULT_AVATAR_URL, getUserDisplayName } from '../data.js';
import * as familyApi from '../api/family.js';

const TREND_METRICS = [
  { id: 'blood_pressure', label: '血压', unit: 'mmHg', chartLabel: '收缩压与舒张压' },
  { id: 'blood_sugar', label: '血糖', unit: 'mmol/L', chartLabel: '血糖趋势' },
  { id: 'heart_rate', label: '心率', unit: 'bpm', chartLabel: '心率趋势' },
];

function StatCard({ tone, icon: Icon, title, value, unit }) {
  return <article className="trends-stat-card">
    <div className="trends-stat-label"><i className={tone}><Icon size={22} /></i><span>{title}</span></div>
    <div className="trends-stat-value"><strong>{value}</strong>{unit ? <span>{unit}</span> : null}</div>
  </article>;
}

export default function HealthTrendsPage({ onNavigate }) {
  const { user } = useAuth();
  const [metric, setMetric] = useState('blood_pressure');
  const [targets, setTargets] = useState([]);
  const [selectedKey, setSelectedKey] = useState('self');
  const selectedTarget = targets.find((item) => item.key === selectedKey) || null;

  const { data, loading, error, empty, execute } = useAsync(healthApi.getHealthTrends, {
    initialData: null,
  });

  useEffect(() => {
    execute({
      metric,
      days: 30,
      memberId: selectedTarget?.memberId ?? null,
      subjectUserId: selectedTarget?.subjectUserId ?? null,
    });
  }, [metric, selectedTarget?.memberId, selectedTarget?.subjectUserId, execute]);

  useEffect(() => {
    familyApi.getPatientTargets()
      .then((items) => setTargets(items.filter((item) => item.kind !== 'account' || item.permissions?.canViewHealth)))
      .catch(() => setTargets([]));
  }, []);

  const records = data?.records || [];
  const stats = data?.stats || {};
  const metricInfo = TREND_METRICS.find(item => item.id === metric) || TREND_METRICS[0];
  const { unit, chartLabel } = metricInfo;
  const recordsEmpty = !loading && !error && records.length === 0;

  const handleFilter = useCallback(() => {
    window.dispatchEvent(new CustomEvent('app:success', { detail: '筛选功能已触发' }));
  }, []);

  const handleDownload = useCallback(() => {
    window.dispatchEvent(new CustomEvent('app:success', { detail: '报告下载已开始' }));
  }, []);

  const handleAddRecord = useCallback(() => {
    if (selectedTarget?.kind === 'account' && !selectedTarget.permissions?.canAddHealth) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: '该家庭成员未授权你录入健康数据' }));
      return;
    }
    if (onNavigate) {
      onNavigate('health-record');
    }
  }, [onNavigate, selectedTarget]);

  const handleError = useCallback(() => {
    window.dispatchEvent(new CustomEvent('app:error', { detail: error }));
  }, [error]);

  const recordsLoadingRender = (
    <div className="trends-records-list"><div className="state-loading"><p>加载中...</p></div></div>
  );

  const recordsEmptyRender = (
    <div className="trends-records-list"><div className="state-empty"><p>暂无记录</p></div></div>
  );

  const recordsErrorRender = (
    <div className="trends-records-list"><div className="state-error"><p>{error}</p></div></div>
  );

  return <div className="stitch-trends">
    <main className="trends-main">
      <header className="trends-page-header"><div><h1>健康趋势</h1><p>随时间跟踪和分析生命体征指标。</p></div><div className="trends-tabs">{TREND_METRICS.map(item => <button className={metric === item.id ? 'active' : ''} onClick={() => setMetric(item.id)} key={item.id}>{item.label}</button>)}</div></header>
      <div className="trends-dashboard">
        <div className="trends-left-column">
          <section className="trends-chart-card">
            <div className="trends-dot-pattern" aria-hidden="true" />
            <div className="trends-chart-heading"><div><h2>{chartLabel}</h2><p>过去 30 天概览</p></div><div><button aria-label="筛选" onClick={handleFilter}><Filter size={20} /></button><button aria-label="下载" onClick={handleDownload}><Download size={20} /></button><button aria-label="更多"><MoreHorizontal size={21} /></button></div></div>
            <HealthChart records={records} label={chartLabel} />
          </section>
          <StateBoundary loading={loading} error={error} empty={recordsEmpty}>
            <div className="trends-stats">
              <StatCard tone="red" icon={Activity} title="最新读数" value={stats.latest || '--'} unit={unit} />
              <StatCard tone="blue" icon={Minus} title="30天平均值" value={stats.average || '--'} unit={unit} />
              <StatCard tone="warning" icon={AlertTriangle} title="峰值读数" value={stats.peak || '--'} unit={unit} />
            </div>
          </StateBoundary>
        </div>
        <aside className="trends-right-column">
          <section className="trends-patient-card"><div className="trends-patient-head"><img src={selectedTarget?.avatarUrl || user?.avatarUrl || DEFAULT_AVATAR_URL} alt={`${selectedTarget?.name || getUserDisplayName(user)}头像`} /><div><h2>{selectedTarget?.name || getUserDisplayName(user)}</h2><p>{selectedTarget?.kind === 'account' ? '已授权家庭账号' : `ID: ${user?.id || '----'}`}</p></div></div><label className="trends-patient-switch"><Users size={14} /><select aria-label="切换健康档案" value={selectedKey} onChange={(event) => setSelectedKey(event.target.value)}><option value="self">自己</option>{targets.map((target) => <option key={target.key} value={target.key}>{target.name}{target.kind === 'account' ? '（共享）' : ''}</option>)}</select></label><div className="trends-patient-tags">{selectedTarget?.relation ? <span>{selectedTarget.relation}</span> : null}{!selectedTarget && user?.gender ? <span>{user.gender}</span> : null}</div><button>查看完整档案</button></section>
          <section className="trends-records-card"><div className="trends-records-heading"><h2>近期记录</h2><button>查看全部</button></div>
            <StateBoundary loading={loading} error={error} empty={recordsEmpty} loadingRender={recordsLoadingRender} emptyRender={recordsEmptyRender} errorRender={recordsErrorRender}>
              <div className="trends-records-list">{records.map(record => <button key={record.recordedDate || record.id}><i><Activity size={21} /></i><span><strong>{record.value} <em>{record.unit || unit}</em></strong><small>{record.recordedDate || '—'}</small></span><ChevronRight size={21} /></button>)}</div>
            </StateBoundary>
            <button className="trends-add" onClick={handleAddRecord}><Plus size={16} />添加新条目</button>
          </section>
        </aside>
      </div>
    </main>
    <footer className="trends-footer"><div><span className="trends-footer-logo"><HeartPulse size={22} fill="currentColor" /><strong>康伴</strong></span><p>© 2025 康伴医疗系统。高保真患者护理。</p><span className="trends-footer-links"><a href="#privacy">隐私政策</a><a href="#terms">服务条款</a></span></div></footer>
  </div>;
}
