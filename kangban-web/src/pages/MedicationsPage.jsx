import { useState, useCallback } from 'react';
import { AlertTriangle, CalendarDays, ChevronDown, ChevronUp, CircleCheck, Clock3, Filter, X } from 'lucide-react';
import * as medApi from '../api/medications.js';
import { useAsync } from '../hooks/useAsync.js';
import { Button, Card, SectionHeading, StatusChip } from '../components/UI.jsx';

function firstReminderTime(times) {
  if (Array.isArray(times)) return times.find(Boolean) || '—';
  try {
    const parsed = JSON.parse(times);
    if (Array.isArray(parsed)) return parsed.find(Boolean) || '—';
  } catch { /* Backward compatibility for older comma-separated records. */ }
  return String(times || '').split(',').find(Boolean) || '—';
}

function toMedicationView(item) {
  const status = item.todayStatus === 'completed'
    ? '今日已服用'
    : item.status === 'paused' ? '已暂停' : '待服用';
  return {
    ...item,
    detail: [item.dosage, item.unit].filter(Boolean).join(' ') || '未填写剂量',
    time: firstReminderTime(item.times),
    statusLabel: status,
  };
}

export default function MedicationsPage({ onNavigate }) {
  const [filter, setFilter] = useState('全部');
  const [expanded, setExpanded] = useState('amlodipine');
  const [dismissWarning, setDismissWarning] = useState(false);
  const [selectedDrugs, setSelectedDrugs] = useState(new Set());
  const [interactionResult, setInteractionResult] = useState(null);
  const [interactionError, setInteractionError] = useState(null);
  const [checkingInteraction, setCheckingInteraction] = useState(false);

  const { data: medicationData = [], loading, error, execute: fetchMedications } = useAsync(medApi.getMedications, {
    immediate: true,
    initialData: [],
  });

  const medications = medicationData.map(toMedicationView);
  const visible = filter === '全部'
    ? medications
    : medications.filter(item => item.statusLabel === filter);

  const handleConfirmDose = useCallback(async (id) => {
    try {
      await medApi.confirmDose(id);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '已确认服药' }));
      fetchMedications();
    } catch (err) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '确认服药失败' }));
    }
  }, [fetchMedications]);

  const toggleDrugSelection = useCallback((id) => {
    setSelectedDrugs((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
    // Clear previous results when selection changes
    setInteractionResult(null);
    setInteractionError(null);
  }, []);

  const handleCheckInteraction = useCallback(async () => {
    if (selectedDrugs.size < 2) return;
    setCheckingInteraction(true);
    setInteractionResult(null);
    setInteractionError(null);
    try {
      const drugIds = Array.from(selectedDrugs).map(String);
      const result = await medApi.checkInteraction(drugIds);
      setInteractionResult(result);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '药物相互作用检查完成' }));
    } catch (err) {
      setInteractionError(err.message || '检查失败，请稍后重试');
      setInteractionResult(null);
    } finally {
      setCheckingInteraction(false);
    }
  }, [selectedDrugs]);

  const handleViewHistory = useCallback(async (medicationId) => {
    if (!medicationId) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: '请先选择一个药品查看历史' }));
      return;
    }
    try {
      const history = await medApi.getMedicationHistory(medicationId);
      if (history?.length > 0) {
        window.dispatchEvent(new CustomEvent('app:success', { detail: `共有 ${history.length} 条用药记录` }));
      } else {
        window.dispatchEvent(new CustomEvent('app:success', { detail: '暂无用药历史记录' }));
      }
    } catch (err) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '获取用药历史失败' }));
    }
  }, []);

  const handlePrint = useCallback(() => {
    window.dispatchEvent(new CustomEvent('app:print', { detail: 'medications' }));
  }, []);

  if (loading) {
    return <main className="page-content"><div className="state-loading"><div className="state-spinner" /><p>加载中...</p></div></main>;
  }

  if (error) {
    return <main className="page-content"><div className="state-error"><p>{error}</p></div></main>;
  }

  const hasInteraction = medications.some(m => m.warning);

  const today = new Date();
  const todayStr = `${today.getFullYear()}年${today.getMonth() + 1}月${today.getDate()}日`;

  return <main className="page-content">
    <div className="page-header">
      <div>
        <div className="page-eyebrow">健康档案</div>
        <h1>用药管理</h1>
        <p className="page-subtitle">查看当前处方，管理您的用药计划。</p>
      </div>
      <div className="header-actions">
        <Button onClick={handlePrint}><CalendarDays size={14} />打印方案</Button>
        <Button variant="primary" onClick={() => onNavigate('medication-add')}>+ 添加药物</Button>
      </div>
    </div>

    {hasInteraction && !dismissWarning && (
      <div className="medication-warning">
        <span className="warning-symbol"><AlertTriangle size={14} /></span>
        <span>
          <strong>检测到药物相互作用警告</strong>
          <p>您正在服用 {medications.filter(m => m.warning).map(m => `${m.name} (${m.detail})`).join(' 与 ')}。一起服用可能会增加出血风险，请在医生指导下调整。</p>
          <Button variant="soft" onClick={() => handleCheckInteraction()}>查看详情</Button>
          <button className="soft" onClick={() => setDismissWarning(true)}>确认</button>
        </span>
        <button aria-label="关闭警告" onClick={() => setDismissWarning(true)}><X size={16} /></button>
      </div>
    )}

    <Card className="medication-timeline-card">
      <SectionHeading title="今日用药时间轴" note={todayStr} />
      <div className="medication-timeline">
        {medications.length > 0 ? (
          medications.map((item, index) => (
            <div key={item.id} className={`med-event ${item.statusLabel === '待服用' ? 'active' : ''}`}>
              <span>{item.time}</span>
              <span className="event-dot" />
              <small>{item.statusLabel === '今日已服用' ? '已服用' : index === medications.length - 1 ? '睡前' : item.statusLabel === '待服用' ? '待服用' : '下一次'}</small>
            </div>
          ))
        ) : (
          <div className="med-event"><span>—</span><span className="event-dot" /><small>暂无药物</small></div>
        )}
      </div>
    </Card>

    {medications.length === 0 ? (
      <div className="state-empty" style={{ marginTop: 16 }}><p>暂无用药数据</p></div>
    ) : (
      <div className="medication-grid">
        <Card className="plans-card">
          <SectionHeading
            title="当前用药方案"
            action={
              <div className="plan-filters" style={{ display: 'flex', gap: 6 }}>
                {['全部', '待服用', '今日已服用'].map(f => (
                  <button
                    key={f}
                    className={`chip ${filter === f ? 'blue' : ''}`}
                    style={{ cursor: 'pointer', background: filter === f ? 'var(--primary-soft)' : 'transparent', border: '1px solid var(--line)', borderRadius: 8, padding: '3px 8px', fontSize: 9 }}
                    onClick={() => setFilter(f)}
                  >
                    {f}
                  </button>
                ))}
                <button className="more-button" aria-label="筛选用药"><Filter size={14} /></button>
              </div>
            }
          />
          <div className="plan-list">
            {visible.map(item => (
              <button
                key={item.id}
                className={`plan-row ${expanded === item.id ? 'active' : ''}`}
                onClick={() => setExpanded(expanded === item.id ? '' : item.id)}
              >
                <span className={`plan-color ${item.color}`} />
                <span>
                  <strong>{item.name}</strong>
                  <small>{item.detail}</small>
                  {expanded === item.id && (
                    <>
                      <small className="plan-expanded">用药提醒：请在饭后服用，遵医嘱完成疗程。</small>
                      <button
                        className="button soft"
                        style={{ marginTop: 8, fontSize: 9, padding: '4px 10px' }}
                        onClick={(e) => { e.stopPropagation(); handleConfirmDose(item.id); }}
                      >
                        <CircleCheck size={12} /> 确认服药
                      </button>
                    </>
                  )}
                </span>
                <span className="plan-meta">
                  <StatusChip tone={item.warning ? 'coral' : item.statusLabel === '待服用' ? 'amber' : 'green'}>{item.statusLabel}</StatusChip>
                  <small>{item.time}</small>
                </span>
                {expanded === item.id ? <ChevronUp size={15} color="var(--muted)" /> : <ChevronDown size={15} color="var(--muted)" />}
              </button>
            ))}
          </div>
          <button className="button soft full-width" onClick={() => {
            const firstMed = visible[0];
            if (firstMed) handleViewHistory(firstMed.id);
            else window.dispatchEvent(new CustomEvent('app:error', { detail: '暂无药品可选择查看历史' }));
          }}>查看完整用药史</button>
        </Card>

        <div>
          <Card className="interaction-card">
            <SectionHeading title="药物相互作用检查" note="演示规则，非医疗建议" />
            <p style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 12 }}>
              选择当前用药方案中的药物，检查是否有已知的药物相互作用。
              <br /><strong>⚠️ 所有规则均为演示数据，不构成医疗建议。请咨询医生或药师。</strong>
            </p>

            {/* Medication multi-select list */}
            <div className="interaction-drug-list">
              {medications.length === 0 ? (
                <p className="state-empty" style={{ padding: '12px 0', fontSize: 13 }}>暂无用药数据，请先添加药物</p>
              ) : (
                medications.map((med) => {
                  const isSelected = selectedDrugs.has(med.id);
                  return (
                    <label
                      key={med.id}
                      className={`interaction-drug-item ${isSelected ? 'selected' : ''}`}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px',
                        border: `1px solid ${isSelected ? 'var(--primary)' : 'var(--line)'}`,
                        borderRadius: 'var(--radius-sm)', cursor: 'pointer', marginBottom: 6,
                        background: isSelected ? 'var(--primary-soft)' : 'var(--surface)',
                        transition: 'all 0.15s',
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggleDrugSelection(med.id)}
                        style={{ accentColor: 'var(--primary)' }}
                      />
                      <span style={{ flex: 1 }}>
                        <strong style={{ fontSize: 13 }}>{med.name}</strong>
                        <small style={{ color: 'var(--muted)', marginLeft: 8 }}>{med.detail}</small>
                      </span>
                    </label>
                  );
                })
              )}
            </div>

            <button
              className="button soft full-width"
              onClick={handleCheckInteraction}
              disabled={checkingInteraction || selectedDrugs.size < 2}
              style={{ marginTop: 12 }}
            >
              {checkingInteraction ? '检查中...' : `检查药物相互作用${selectedDrugs.size >= 2 ? ` (${selectedDrugs.size} 种)` : ''}`}
            </button>
            {selectedDrugs.size < 2 && selectedDrugs.size > 0 && (
              <p style={{ fontSize: 11, color: 'var(--muted)', marginTop: 6, textAlign: 'center' }}>
                请至少选择 2 种药物进行检查
              </p>
            )}

            {/* Interaction results */}
            {interactionResult && (
              <div className="interaction-results" style={{ marginTop: 16 }}>
                {/* --- Risk badge --- */}
                {interactionResult.overallRiskLevel && (
                  <div className={`interaction-risk-badge risk-${interactionResult.overallRiskLevel}`}
                    style={{
                      padding: '8px 16px', borderRadius: 'var(--radius-sm)', marginBottom: 12,
                      textAlign: 'center', fontWeight: 700, fontSize: 14,
                      background: interactionResult.overallRiskLevel === 'high' ? '#fde8e8'
                        : interactionResult.overallRiskLevel === 'medium' ? '#fef3c7' : '#d1fae5',
                      color: interactionResult.overallRiskLevel === 'high' ? '#b91c1c'
                        : interactionResult.overallRiskLevel === 'medium' ? '#92400e' : '#065f46',
                    }}
                  >
                    {interactionResult.overallRiskLevel === 'high' ? '🔴 高风险相互作用'
                      : interactionResult.overallRiskLevel === 'medium' ? '🟡 中风险相互作用'
                      : '🟢 低风险相互作用'}
                  </div>
                )}

                {/* --- Summary --- */}
                {interactionResult.summary && (
                  <p style={{ fontSize: 13, fontWeight: 600, marginBottom: 12, lineHeight: 1.5 }}>
                    {interactionResult.summary}
                  </p>
                )}

                {/* --- Matched rules --- */}
                {interactionResult.matchedRules?.length > 0 && (
                  <div className="interaction-matched-rules">
                    {interactionResult.matchedRules.map((rule, idx) => (
                      <div key={idx} className={`interaction-rule-card risk-${rule.riskLevel}`}
                        style={{
                          padding: 12, borderRadius: 'var(--radius-sm)', marginBottom: 10,
                          border: '1px solid var(--line)', background: 'var(--canvas)',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                          <strong style={{ fontSize: 13 }}>{rule.drugA} + {rule.drugB}</strong>
                          <span className={`risk-chip risk-${rule.riskLevel}`}
                            style={{
                              padding: '2px 8px', borderRadius: 999, fontSize: 10, fontWeight: 700,
                              background: rule.riskLevel === 'high' ? 'var(--primary)' :
                                rule.riskLevel === 'medium' ? '#f59e0b' : '#10b981',
                              color: '#fff',
                            }}
                          >
                            {rule.riskLevel === 'high' ? '高风险' : rule.riskLevel === 'medium' ? '中风险' : '低风险'}
                          </span>
                        </div>
                        <p style={{ fontSize: 12, color: 'var(--muted)', margin: '0 0 6px', lineHeight: 1.5 }}>
                          {rule.description}
                        </p>
                        <p style={{ fontSize: 12, color: 'var(--text)', margin: '0 0 4px', lineHeight: 1.5 }}>
                          <strong>建议：</strong>{rule.advice}
                        </p>
                        <small style={{ fontSize: 10, color: 'var(--muted)' }}>
                          来源：{rule.source}
                        </small>
                      </div>
                    ))}
                  </div>
                )}

                {/* --- Uncovered pairs --- */}
                {interactionResult.uncoveredPairs?.length > 0 && (
                  <div className="interaction-uncovered"
                    style={{
                      padding: 12, borderRadius: 'var(--radius-sm)', marginBottom: 10,
                      border: '1px dashed var(--line)', background: 'var(--canvas-soft)',
                    }}
                  >
                    <p style={{ fontSize: 12, fontWeight: 600, margin: '0 0 8px', color: 'var(--muted)' }}>
                      ⚠️ 暂无演示规则覆盖的组合
                    </p>
                    {interactionResult.uncoveredPairs.map((pair, idx) => (
                      <p key={idx} style={{ fontSize: 12, color: 'var(--muted)', margin: '2px 0' }}>
                        {pair.drugA} + {pair.drugB} — {pair.message}，建议咨询医生或药师
                      </p>
                    ))}
                  </div>
                )}

                {/* --- No interaction or all uncovered --- */}
                {!interactionResult.hasInteraction && !interactionResult.overallRiskLevel && (
                  <div className="interaction-no-result"
                    style={{
                      padding: 12, borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--line)', background: 'var(--canvas-soft)',
                      textAlign: 'center',
                    }}
                  >
                    <p style={{ fontSize: 13, color: 'var(--muted)', margin: 0 }}>
                      {interactionResult.summary || '暂无演示规则覆盖，建议咨询医生或药师'}
                    </p>
                  </div>
                )}

                {/* --- Disclaimer --- */}
                {interactionResult.disclaimer && (
                  <p style={{
                    fontSize: 11, color: 'var(--muted)', marginTop: 12,
                    padding: '8px 12px', borderRadius: 'var(--radius-sm)',
                    background: '#fff3cd', border: '1px solid #ffc107',
                  }}>
                    ⚠️ {interactionResult.disclaimer}
                  </p>
                )}
              </div>
            )}

            {/* Error state */}
            {interactionError && (
              <div className="state-error" style={{ marginTop: 12 }}>
                <p style={{ fontSize: 12 }}>{interactionError}</p>
              </div>
            )}
          </Card>

          <Card className="dosage-card">
            <h3>月剂量统计</h3>
            <p>本月已服用 {medications.length > 0 ? Math.round(medications.filter(m => m.statusLabel === '今日已服用').length / medications.length * 100) : 0}% 的计划剂量</p>
            <div className="dosage-value">
              <strong>{medications.filter(m => m.statusLabel === '今日已服用').length}.0</strong>
              <span>剩余 {medications.filter(m => m.statusLabel !== '今日已服用').length}.0 mg</span>
            </div>
          </Card>
        </div>
      </div>
    )}
  </main>;
}
