import { useState, useEffect } from 'react';
import { Activity, CalendarDays, CheckCircle2, Droplets, Heart, Plus, Scale, Watch } from 'lucide-react';
import * as healthApi from '../api/health.js';
import * as familyApi from '../api/family.js';
import { buildHealthRecordPayload } from '../api/contracts.js';

const metrics = [
  { id: 'heart', label: '心率', unit: 'bpm', icon: Heart, metricKey: 'heart_rate' },
  { id: 'pressure', label: '血压', unit: 'mmHg', icon: Activity, metricKey: 'blood_pressure' },
  { id: 'glucose', label: '血糖', unit: 'mmol/L', icon: Droplets, metricKey: 'blood_sugar' },
  { id: 'weight', label: '体重', unit: 'kg', icon: Scale, metricKey: 'weight' },
];

export default function HealthRecordPage({ onNavigate }) {
  const [memberId, setMemberId] = useState(null);
  const [members, setMembers] = useState([]);
  const [metric, setMetric] = useState('heart');
  const [value, setValue] = useState('');
  const [systolic, setSystolic] = useState('');
  const [diastolic, setDiastolic] = useState('');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [time, setTime] = useState(new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }));
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');
  const [trendData, setTrendData] = useState(null);

  const selected = metrics.find((item) => item.id === metric) ?? metrics[0];
  const selectedMember = members.find((item) => item.id === memberId);
  const memberName = selectedMember?.name || '自己';

  useEffect(() => {
    familyApi.getFamilyMembers().then(setMembers).catch(() => setMembers([]));
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const data = await healthApi.getHealthTrends({ metric: selected.metricKey, memberId, days: 30 });
        setTrendData(data);
      } catch { /* ignore */ }
    })();
  }, [memberId, metric, selected.metricKey]);

  function chooseMetric(next) {
    setMetric(next);
    setSaved(false);
    setError('');
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!value.trim() && metric !== 'pressure') {
      setError('请输入测量数值');
      return;
    }
    if (metric === 'pressure' && (!systolic.trim() || !diastolic.trim())) {
      setError('请输入收缩压和舒张压');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const payload = buildHealthRecordPayload({
        memberId,
        metric: selected.metricKey,
        value: metric === 'pressure' ? `${systolic}/${diastolic}` : value,
        unit: selected.unit,
        recordedDate: date,
        recordedTime: time,
        note,
      });
      await healthApi.addHealthRecord(payload);
      setSaved(true);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '健康指标已记录' }));
    } catch (err) {
      setError(err.message || '保存失败');
    } finally {
      setLoading(false);
    }
  };

  const avgValue = trendData?.stats?.average || '—';
  const chartValues = (trendData?.records || []).slice(0, 12).reverse().map((record) => Number(String(record.value).split('/')[0])).filter(Number.isFinite);
  const chartMin = chartValues.length ? Math.min(...chartValues) : 0;
  const chartMax = chartValues.length ? Math.max(...chartValues) : 0;
  const chartHeights = chartValues.map((item) => chartMax === chartMin ? 55 : 20 + ((item - chartMin) / (chartMax - chartMin)) * 70);

  return <main className="page-content stitch-form-page health-record-page">
    <header className="stitch-form-heading"><div><h1>记录健康指标</h1><p>准确记录，为康伴 AI 提供更精准的健康分析依据。</p></div></header>
    {error && <p className="auth-error" style={{ marginBottom: 8 }}>{error}</p>}
    <div className="health-record-layout">
      <form className="health-record-form" onSubmit={handleSubmit}>
        <section className="record-block"><span>记录对象</span><div className="member-picker">
          {[{ id: null, name: '自己' }, ...members].map((item) => <button type="button" key={item.id ?? 'self'} className={memberId === item.id ? 'selected' : ''} onClick={() => { setMemberId(item.id); setSaved(false); }}>
            <img src="/stitch/PRD-UI-Prototype-Implementation/health-metrics-entry/assets/01.jpg" alt="" />{item.name}{memberId === item.id && <CheckCircle2 size={14} />}
          </button>)}
          <button type="button" className="add-member" onClick={() => onNavigate('family-add')}><Plus size={14} />添加成员</button>
        </div></section>
        <section className="record-block"><span>指标类型</span><div className="record-metric-picker">
          {metrics.map(({ id, label, icon: Icon }) => <button type="button" key={id} className={metric === id ? 'selected' : ''} onClick={() => chooseMetric(id)}><Icon size={19} /><b>{label}</b></button>)}
        </div></section>
        <section className="record-block">
          <span>测量数值</span>
          {metric === 'pressure' ? (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <label className="record-value" style={{ flex: 1 }}>
                <input aria-label="收缩压" placeholder="120" inputMode="decimal" value={systolic} onChange={e => setSystolic(e.target.value)} />
                <small>mmHg</small>
              </label>
              <span style={{ color: 'var(--muted)' }}>/</span>
              <label className="record-value" style={{ flex: 1 }}>
                <input aria-label="舒张压" placeholder="80" inputMode="decimal" value={diastolic} onChange={e => setDiastolic(e.target.value)} />
                <small>mmHg</small>
              </label>
            </div>
          ) : (
            <label className="record-value">
              <input aria-label="测量数值" value={value} onChange={(event) => { setValue(event.target.value); setSaved(false); }} inputMode="decimal" />
              <small>{selected.unit}</small>
            </label>
          )}
        </section>
        <section className="record-dual">
          <label><span>日期</span><div><CalendarDays size={15} /><input aria-label="日期" type="date" value={date} onChange={e => setDate(e.target.value)} /></div></label>
          <label><span>时间</span><div><Watch size={15} /><input aria-label="时间" type="time" value={time} onChange={e => setTime(e.target.value)} /></div></label>
        </section>
        <section className="record-block"><span>备注（选填）</span><textarea aria-label="备注" placeholder="例如：测量后感觉良好，精神不错。" value={note} onChange={e => setNote(e.target.value)} /></section>
        <button className="record-submit" type="submit" disabled={loading}>
          <CheckCircle2 size={16} />{loading ? '保存中...' : saved ? '记录已保存' : '保存记录'}
        </button>
        {saved && <p className="form-success">已为{memberName}保存本次{selected.label}记录。</p>}
      </form>
      <aside className="health-preview">
        <section className="wearable-card">
          <div><span><Watch size={14} />Apple Watch</span><b>已连接</b></div>
          <p>上次同步：今天，08:30</p>
          <strong>{value || systolic ? `${systolic || ''}${diastolic ? `/${diastolic}` : ''}` : '—'}<small>{selected.unit}</small></strong>
        </section>
        <section className="pulse-preview">
          <header><div><b>{selected.label}趋势</b><span><i />正常</span></div><button type="button">本月</button></header>
          <p><Heart size={12} />数据将与历史记录同步。</p>
          {chartHeights.length > 0
            ? <div className="pulse-grid" aria-hidden="true">{chartHeights.map((height, index) => <i key={index} style={{ height: `${height}%` }} />)}</div>
            : <p className="state-empty">暂无历史趋势数据</p>}
          <footer>
            <span>本周平均<strong>{avgValue} {selected.unit}</strong></span>
            <span>静息<strong>—</strong></span>
          </footer>
        </section>
      </aside>
    </div>
  </main>;
}
