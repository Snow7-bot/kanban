function chartValues(records) {
  return (records || []).slice().reverse().map(record => Number.parseFloat(String(record.value || '').split('/')[0])).filter(Number.isFinite).slice(-10);
}

export default function HealthChart({ records, label = '健康指标' }) {
  const values = chartValues(records);
  if (!values.length) {
    return <div className="trends-chart trends-chart-empty"><p>暂无趋势数据</p></div>;
  }

  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = Math.max(max - min, 1);
  const axisMin = Math.floor(min - span * 0.1);
  const axisMax = Math.ceil(max + span * 0.1);
  const axisMiddle = Math.round((axisMin + axisMax) / 2);
  const chartRecords = (records || []).slice().reverse().filter(record => Number.isFinite(Number.parseFloat(String(record.value || '').split('/')[0]))).slice(-10);
  const firstDate = chartRecords[0]?.recordedDate || '—';
  const lastDate = chartRecords.at(-1)?.recordedDate || firstDate;
  const points = values.map((value, index) => {
    const x = values.length === 1 ? 400 : (index / (values.length - 1)) * 800;
    const y = 235 - ((value - min) / span) * 150;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');

  return <div className="trends-chart" aria-label={`${label}趋势图`}>
    <div className="trends-chart-canvas">
      <div className="trends-y-axis"><span>{axisMax}</span><span>{axisMiddle}</span><span>{axisMin}</span></div>
      <svg viewBox="0 0 800 300" preserveAspectRatio="none" role="img" aria-hidden="true">
        <defs>
          <linearGradient id="trend-systolic" x1="0%" y1="0%" x2="0%" y2="100%"><stop offset="0%" stopColor="#e63946" stopOpacity=".2" /><stop offset="100%" stopColor="#e63946" stopOpacity="0" /></linearGradient>
        </defs>
        <polyline points={`0,250 ${points} 800,250`} fill="url(#trend-systolic)" />
        <polyline points={points} fill="none" stroke="#e63946" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        <line x1="0" y1="50" x2="800" y2="50" stroke="#e4bebc" strokeWidth="1" strokeDasharray="4" opacity=".3" />
        <line x1="0" y1="150" x2="800" y2="150" stroke="#e4bebc" strokeWidth="1" strokeDasharray="4" opacity=".3" />
        <line x1="0" y1="250" x2="800" y2="250" stroke="#e4bebc" strokeWidth="1" opacity=".5" />
      </svg>
    </div>
    <div className="trends-x-axis"><span>{firstDate}</span><span>{lastDate}</span></div>
  </div>;
}
