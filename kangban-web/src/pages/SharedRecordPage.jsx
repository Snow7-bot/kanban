import { useState, useEffect } from 'react';
import { ArrowLeft, FileScan, Info, Pill, HeartPulse, CheckCircle2, ClipboardPlus } from 'lucide-react';
import * as recordsApi from '../api/medicalRecords.js';
import { mapRecordDetail } from './recordDetailData.js';

function DetailSection({ icon: Icon, tone, title, children }) {
  return <section className="record-detail-card"><header><i className={tone}><Icon size={18} /></i><h2>{title}</h2></header>{children}</section>;
}

export default function SharedRecordPage({ onNavigate }) {
  const [record, setRecord] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [errorCode, setErrorCode] = useState(0);

  useEffect(() => {
    const hash = window.location.hash;
    // #/shared-record/TOKEN or #/shared-record?token=TOKEN
    let token = '';
    const clean = hash.replace(/^#\/?/, '');
    const parts = clean.split('/');
    if (parts.length >= 3 && parts[1] === 'shared-record') {
      token = parts.slice(2).join('/');
    } else {
      const params = new URLSearchParams(clean.split('?')[1] || '');
      token = params.get('token') || '';
    }

    if (!token) {
      setError('无效的分享链接');
      setLoading(false);
      return;
    }

    (async () => {
      try {
        const data = await recordsApi.viewSharedRecord(token);
        setRecord(data);
      } catch (err) {
        setError(err.message || '加载分享病历失败');
        if (err.code === 404) {
          setErrorCode(404);
        } else if (err.code === 410) {
          setErrorCode(410);
        } else if (err.code === 401) {
          setErrorCode(401);
        }
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  if (loading) {
    return (
      <main className="page-content record-detail-page">
        <div className="state-loading">
          <div className="state-spinner" />
          <p>加载分享病历...</p>
        </div>
      </main>
    );
  }

  if (error) {
    const icon = errorCode === 410 ? '⏰' : errorCode === 404 ? '🔍' : errorCode === 401 ? '🔒' : '⚠️';
    const title = errorCode === 410 ? '分享已失效'
      : errorCode === 404 ? '未找到病历'
      : errorCode === 401 ? '需要登录'
      : '加载失败';
    const desc = errorCode === 410 ? '此分享链接已过期或已被撤销。请联系分享者重新生成链接。'
      : errorCode === 404 ? '该病历可能已被删除或分享链接不正确。'
      : errorCode === 401 ? '请先登录后再访问分享链接。'
      : error;

    return (
      <main className="page-content record-detail-page">
        <div className="shared-error-state">
          <div className="shared-error-icon">{icon}</div>
          <h2>{title}</h2>
          <p>{desc}</p>
          <div className="shared-error-actions">
            <button className="btn-primary" onClick={() => onNavigate('home')}>返回首页</button>
            <button className="btn-cancel" onClick={() => onNavigate('records')}>病历管理</button>
          </div>
        </div>
      </main>
    );
  }

  if (!record) {
    return (
      <main className="page-content record-detail-page">
        <div className="state-empty"><p>未找到分享的病历记录</p></div>
      </main>
    );
  }

  const {
    recordDate, recordType, recordName, hospital, department, doctor, confidence,
    chiefComplaint, diagnoses, medications, advices,
  } = mapRecordDetail(record);

  return (
    <main className="record-detail-page">
      <header className="record-detail-header">
        <div>
          <button className="record-back" aria-label="返回" onClick={() => onNavigate('home')}>
            <ArrowLeft size={18} />
          </button>
          <span>
            <h1>分享病历</h1>
            <p>{recordDate}{recordType ? ` · ${recordType}` : ''}{recordName ? ` · ${recordName}` : ''}</p>
          </span>
        </div>
        <div className="record-detail-actions">
          <span className="shared-badge">📤 来自分享</span>
        </div>
      </header>

      <div className="record-detail-content">
        <div className="record-detail-main">
          {/* 医院/科室/医生信息 */}
          {(hospital || department || doctor) ? (
            <section className="record-meta">
              {hospital && <div><small>就诊医院</small><strong>{hospital}</strong></div>}
              {department && <div><small>就诊科室</small><strong>{department}</strong></div>}
              {doctor && <div><small>主治医师</small><strong>{doctor}</strong></div>}
            </section>
          ) : null}

          {/* AI 诊断结果 */}
          <DetailSection icon={ClipboardPlus} tone="red" title="AI 结构化诊断" action={
            confidence !== null ? <span className="record-confidence"><i />解析置信度 {confidence}%</span> : null
          }>
            {diagnoses.length > 0 ? (
              <>
                {chiefComplaint && <div className="record-field"><h3>主诉</h3><p>{chiefComplaint}</p></div>}
                <div className="record-field"><h3>临床诊断</h3>
                  <div className="diagnosis-list">
                    {diagnoses.map((d, i) => (
                      <article key={i} className={d.warning ? 'warning' : ''}>
                        {d.warning ? <HeartPulse size={17} /> : <Info size={17} />}
                        <span><strong>{d.name}</strong>{d.detail ? <small>{d.detail}</small> : null}</span>
                      </article>
                    ))}
                  </div>
                </div>
              </>
            ) : (
              <p style={{ color: 'var(--muted)', padding: '12px 0' }}>暂无解析结果</p>
            )}
          </DetailSection>

          {/* 处方用药 */}
          <div className="record-detail-split">
            <DetailSection icon={Pill} tone="blue" title="处方用药">
              {medications.length > 0 ? (
                <div className="medicine-list">
                  {medications.map((med, i) => (
                    <article key={i}>
                      <div><strong>{med.name || med.medicationName || '未知药品'}</strong>{med.spec ? <span>{med.spec}</span> : null}</div>
                      {med.usage ? <p>用法：{med.usage}</p> : null}
                    </article>
                  ))}
                </div>
              ) : (
                <p style={{ color: 'var(--muted)', padding: '12px 0' }}>暂无解析结果</p>
              )}
            </DetailSection>

            {/* 医嘱与随访 */}
            <DetailSection icon={HeartPulse} tone="gray" title="医嘱与随访">
              {advices.length > 0 ? (
                <ul className="record-advice">
                  {advices.map((advice, i) => (
                    <li key={i} className={i === advices.length - 1 ? 'follow-up' : ''}>
                      {i === advices.length - 1 ? <CheckCircle2 size={16} /> : <CheckCircle2 size={16} />}{advice}
                    </li>
                  ))}
                </ul>
              ) : (
                <p style={{ color: 'var(--muted)', padding: '12px 0' }}>暂无解析结果</p>
              )}
            </DetailSection>
          </div>
        </div>

        {/* 原件扫描副本 */}
        {record.fileUrl && (
          <aside className="record-scan">
            <header><span><FileScan size={16} />原件扫描副本</span></header>
            <div className="record-scan-image">
              <img src={record.fileUrl} alt="原始病历扫描件" />
            </div>
            <footer><FileScan size={17} /><span><strong>康伴 AI 视觉引擎</strong><small>已完成 OCR 识别与医学语义解析</small></span></footer>
          </aside>
        )}
      </div>
    </main>
  );
}
