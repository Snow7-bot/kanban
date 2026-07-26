import { useState } from 'react';
import { ArrowLeft, Baby, Calculator, CalendarClock, Camera, Clock3, Pill, Plus, ScanLine, Search, X } from 'lucide-react';
import * as medApi from '../api/medications.js';
import { buildMedicationPayload } from '../api/contracts.js';

const instructions = ['饭后服用', '饭前服用', '随餐服用', '无特殊要求'];

export default function MedicationAddPage({ onNavigate }) {
  const [name, setName] = useState('');
  const [age, setAge] = useState('');
  const [weight, setWeight] = useState('');
  const [dosage, setDosage] = useState('1');
  const [unit, setUnit] = useState('片');
  const [inventory, setInventory] = useState('30');
  const [instruction, setInstruction] = useState('饭后服用');
  const [frequency, setFrequency] = useState('每天');
  const [times, setTimes] = useState(['08:00', '20:00']);
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const calculateChildDose = () => setNotice(age && weight ? `已记录儿童参数：${age} 岁、${weight} kg。儿童剂量请以医生或药师确认的方案为准。` : '请先填写儿童年龄和体重，再由医生或药师确认剂量。');

  const addTime = () => setTimes([...times, '12:00']);
  const removeTime = (index) => setTimes(times.filter((_, itemIndex) => itemIndex !== index));

  const saveMedication = async () => {
    if (!name.trim()) {
      setError('请先填写药品名称');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await medApi.addMedication(buildMedicationPayload({
        name: name.trim(),
        dosage,
        unit,
        instruction,
        frequency,
        inventory: parseInt(inventory) || 0,
        times,
      }));
      setNotice(`“${name}”已保存到用药计划。`);
      window.dispatchEvent(new CustomEvent('app:success', { detail: `“${name}”已添加到用药计划` }));
      setTimeout(() => onNavigate('medications'), 1200);
    } catch (err) {
      setError(err.message || '保存失败');
    } finally {
      setLoading(false);
    }
  };

  return <main className="medication-add-page">
    <header className="medication-add-header">
      <div><button aria-label="返回用药管理" onClick={() => onNavigate('medications')}><ArrowLeft size={16} />返回用药管理</button><h1>添加新药品</h1><p>设置精确的用药计划以确保疗效。</p></div>
      <button className="scan-entry" onClick={() => setNotice('扫码录入功能已准备好。')}><ScanLine size={17} />扫码录入</button>
    </header>
    {error && <p className="auth-error" style={{ textAlign: 'center', margin: '8px 0' }}>{error}</p>}
    <section className="medication-add-layout">
      <article className="medication-basic-card">
        <h2><Pill size={18} />基本信息</h2>
        <div className="form-group medicine-name">
          <div><label>药品名称</label><button onClick={calculateChildDose}><Calculator size={15} />儿童剂量换算</button></div>
          <span><Search size={16} /><input aria-label="药品名称" value={name} onChange={(event) => setName(event.target.value)} placeholder="例如：阿司匹林、降压药..." /></span>
        </div>
        <section className="child-dose-card">
          <header><Baby size={15} />儿童患者参数（可选）</header>
          <div><label>年龄（岁）<input aria-label="儿童年龄" value={age} onChange={(event) => setAge(event.target.value)} inputMode="decimal" placeholder="例如：3.5" /></label><label>体重（kg）<input aria-label="儿童体重" value={weight} onChange={(event) => setWeight(event.target.value)} inputMode="decimal" placeholder="例如：15.2" /></label></div>
          <button onClick={calculateChildDose}>计算建议剂量</button>
        </section>
        <div className="dose-grid">
          <label>单次剂量<span><input aria-label="单次剂量" value={dosage} onChange={(event) => setDosage(event.target.value)} inputMode="decimal" /><select aria-label="剂量单位" value={unit} onChange={(event) => setUnit(event.target.value)}>{['片', '粒', 'ml', 'mg'].map(item => <option key={item}>{item}</option>)}</select></span></label>
          <label>当前库存（可选）<span><input aria-label="当前库存" value={inventory} onChange={(event) => setInventory(event.target.value)} inputMode="numeric" /><b>单位</b></span></label>
        </div>
        <div className="form-group"><label>用药说明</label><div className="instruction-list">{instructions.map(item => <button className={instruction === item ? 'active' : ''} onClick={() => setInstruction(item)} key={item}>{item}</button>)}</div></div>
      </article>
      <aside className="medication-schedule">
        <section className="schedule-card">
          <h2><CalendarClock size={18} />用药频率</h2>
          <select aria-label="用药频率" value={frequency} onChange={(event) => setFrequency(event.target.value)}>{['每天', '隔天一次', '每周特定日期', '按需服用'].map(item => <option key={item}>{item}</option>)}</select>
          <div className="reminder-times">
            <label>提醒时间</label>
            {times.map((time, index) => <div key={`${time}-${index}`}>
              <input aria-label={`提醒时间 ${index + 1}`} type="time" value={time} onChange={(event) => setTimes(times.map((item, itemIndex) => itemIndex === index ? event.target.value : item))} /><Clock3 size={14} />
              <button aria-label={`删除提醒时间 ${index + 1}`} onClick={() => removeTime(index)} disabled={times.length === 1}><X size={14} /></button>
            </div>)}
            <button className="add-time" onClick={addTime}><Plus size={14} />添加时间</button>
          </div>
        </section>
        <section className="medication-smart-card">
          <Camera size={70} /><h3>智能分析</h3>
          <p>基于您的病历，此药物可能与当前服用的降压药有轻微相互作用。</p>
          <button onClick={() => window.dispatchEvent(new CustomEvent('app:success', { detail: '药物相互作用详情' }))}>查看详情</button>
        </section>
      </aside>
    </section>
    <footer className="medication-add-footer">
      <button onClick={() => onNavigate('medications')}>取消</button>
      <button onClick={saveMedication} disabled={loading}>{loading ? '保存中...' : '保存药品'}</button>
    </footer>
    {notice && <div className="medication-add-notice" role="status">{notice}</div>}
  </main>;
}
