import { useState, useEffect } from 'react';
import { AlarmClockPlus, ArrowLeft, CheckCircle2, ClipboardPlus, Copy, FileScan, HeartPulse, Info, Pencil, Pill, Printer, RefreshCw, Search, Share2, Trash2, X, ZoomIn } from 'lucide-react';
import * as recordsApi from '../api/medicalRecords.js';
import { mapRecordDetail } from './recordDetailData.js';

function DetailSection({ icon: Icon, tone, title, children, action }) {
  return <section className="record-detail-card"><header><i className={tone}><Icon size={18} /></i><h2>{title}</h2>{action}</header>{children}</section>;
}

export default function RecordDetailPage({ onNavigate }) {
  const [status, setStatus] = useState('');
  const [zoomed, setZoomed] = useState(false);
  const [record, setRecord] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [shareDialog, setShareDialog] = useState({ open: false, data: null, loading: false, error: null });
  const [shareCopied, setShareCopied] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.split('?')[1] || '');
    const id = params.get('id');
    (async () => {
      try {
        const data = id ? await recordsApi.getMedicalRecord(id) : null;
        setRecord(data);
      } catch (err) {
        setError(err.message || '加载病历详情失败');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const getIdFromHash = () => {
    const params = new URLSearchParams(window.location.hash.split('?')[1] || '');
    return params.get('id');
  };

  const openShareDialog = async () => {
    const id = getIdFromHash();
    if (!id) return;
    setShareDialog({ open: true, data: null, loading: true, error: null });
    setShareCopied(false);
    try {
      const status = await recordsApi.getShareStatus(id);
      if (status?.shared) {
        setShareDialog({ open: true, data: status, loading: false, error: null });
      } else {
        const result = await recordsApi.shareMedicalRecord(id);
        setShareDialog({ open: true, data: result, loading: false, error: null });
      }
    } catch (err) {
      setShareDialog({ open: true, data: null, loading: false, error: err.message || '分享失败' });
    }
  };

  const handleCopyShareLink = () => {
    if (shareDialog.data?.shareUrl) {
      navigator.clipboard.writeText(shareDialog.data.shareUrl).then(() => {
        setShareCopied(true);
        setTimeout(() => setShareCopied(false), 3000);
      });
    }
  };

  const handleRevokeShare = async () => {
    const id = getIdFromHash();
    if (!id) return;
    try {
      await recordsApi.revokeShare(id);
      setShareDialog({ open: false, data: null, loading: false, error: null });
      window.dispatchEvent(new CustomEvent('app:success', { detail: '分享已撤销' }));
    } catch (err) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '撤销失败' }));
    }
  };

  const handleDownloadPdf = (includeAnalysis = false) => {
    const id = getIdFromHash();
    if (!id) return;
    recordsApi.downloadPdf(id, includeAnalysis)
      .then(() => window.dispatchEvent(new CustomEvent('app:success', { detail: includeAnalysis ? 'PDF（含AI分析）已开始下载' : 'PDF 已开始下载' })))
      .catch((err) => window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '下载失败' })));
  };

  const formatExpiry = (expiresAt) => {
    if (!expiresAt) return '';
    return new Date(expiresAt).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
  };

  const handleDelete = async () => {
    try {
      const params = new URLSearchParams(window.location.hash.split('?')[1] || '');
      const id = params.get('id');
      if (id) await recordsApi.deleteMedicalRecord(id);
      window.dispatchEvent(new CustomEvent('app:success', { detail: '病历已删除' }));
      onNavigate('records');
    } catch (err) {
      setError(err.message || '删除失败');
    }
    setShowDeleteConfirm(false);
  };

  if (loading) return <main className="page-content"><div className="state-loading"><div className="state-spinner" /><p>加载中...</p></div></main>;
  if (error) return <main className="page-content"><div className="state-error"><p>{error}</p></div></main>;
  if (!record) return <main className="page-content"><div className="state-empty"><p>未找到病历记录</p></div></main>;

  const {
    recordDate, recordType, recordName, hospital, department, doctor, confidence, fileUrl,
    chiefComplaint, diagnoses, medications, advices,
  } = mapRecordDetail(record);

  return <main className="record-detail-page">
    <header className="record-detail-header">
      <div><button className="record-back" aria-label="返回病历管理" onClick={() => onNavigate('records')}><ArrowLeft size={18} /></button><span><h1>病历详情</h1><p>{recordDate}{recordType ? ` · ${recordType}` : ''}{recordName ? ` · ${recordName}` : ''}</p></span></div>
      <div className="record-detail-actions">
        <button onClick={() => setStatus('编辑模式已开启')}><Pencil size={15} />编辑</button>
        <button onClick={() => setStatus('病历已重新解析')}><RefreshCw size={15} />重新解析</button>
        <button onClick={() => handleDownloadPdf(false)} title="下载PDF"><Printer size={15} />导出PDF</button>
        <button onClick={() => handleDownloadPdf(true)} title="下载PDF（含AI分析）"><Printer size={15} /><small style={{marginLeft:2}}>+AI</small></button>
        <button onClick={openShareDialog} title="分享"><Share2 size={15} />分享</button>
        <button className="danger" aria-label="删除病历" onClick={() => setShowDeleteConfirm(true)}><Trash2 size={15} /></button>
      </div>
    </header>
    {showDeleteConfirm && (
      <div className="confirm-dialog">
        <div className="confirm-dialog-card">
          <h3>确认删除</h3>
          <p>确定要删除此病历吗？此操作不可恢复。</p>
          <div className="confirm-actions">
            <button className="btn-cancel" onClick={() => setShowDeleteConfirm(false)}>取消</button>
            <button className="btn-danger" onClick={handleDelete}>确认删除</button>
          </div>
        </div>
      </div>
    )}
    <div className="record-detail-content">
      <div className="record-detail-main">
        {/* 医院/科室/医生信息 — 仅在有数据时显示 */}
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
                    {i === advices.length - 1 ? <AlarmClockPlus size={16} /> : <CheckCircle2 size={16} />}{advice}
                  </li>
                ))}
              </ul>
            ) : (
              <p style={{ color: 'var(--muted)', padding: '12px 0' }}>暂无解析结果</p>
            )}
            <button className="follow-up-button" onClick={() => setStatus('已添加随访提醒')}><AlarmClockPlus size={16} />添加随访提醒</button>
          </DetailSection>
        </div>
      </div>

      {/* 原件扫描副本 */}
      <aside className={`record-scan ${zoomed ? 'zoomed' : ''}`}>
        <header><span><FileScan size={16} />原件扫描副本</span><button aria-label="放大扫描件" onClick={() => setZoomed(!zoomed)}>{zoomed ? <Search size={16} /> : <ZoomIn size={16} />}</button></header>
        <div className="record-scan-image">
          {fileUrl ? (
            <img src={fileUrl} alt="原始病历扫描件" />
          ) : (
            <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--muted)' }}>
              <FileScan size={40} style={{ opacity: 0.3, marginBottom: 12 }} />
              <p>暂无扫描文件</p>
            </div>
          )}
        </div>
        <footer><FileScan size={17} /><span><strong>康伴 AI 视觉引擎</strong><small>已完成 OCR 识别与医学语义解析</small></span></footer>
      </aside>
    </div>
    {status && <div className="record-status" role="status">{status}</div>}

    {/* ---- Share Dialog ---- */}
    {shareDialog.open && (
      <div className="confirm-dialog" onClick={() => setShareDialog({ open: false, data: null, loading: false, error: null })}>
        <div className="confirm-dialog-card share-dialog" onClick={(e) => e.stopPropagation()}>
          <div className="share-dialog-header">
            <h3><Share2 size={16} /> 分享病历</h3>
            <button className="share-dialog-close" onClick={() => setShareDialog({ open: false, data: null, loading: false, error: null })}><X size={16} /></button>
          </div>
          {shareDialog.loading && <div className="state-loading"><div className="state-spinner" /><p>生成分享链接...</p></div>}
          {shareDialog.error && <div className="state-error"><p>{shareDialog.error}</p></div>}
          {!shareDialog.loading && !shareDialog.error && shareDialog.data?.shared !== false && (
            <div className="share-dialog-body">
              <div className="share-link-field">
                <label>分享链接</label>
                <div className="share-link-input-group">
                  <input type="text" readOnly value={shareDialog.data?.shareUrl || ''} onClick={(e) => e.target.select()} />
                  <button className="btn-primary" onClick={handleCopyShareLink}><Copy size={14} />{shareCopied ? '已复制' : '复制'}</button>
                </div>
              </div>
              {shareDialog.data?.expiresAt && <p className="share-expiry">有效期至：{formatExpiry(shareDialog.data.expiresAt)}</p>}
              <p className="share-hint">分享链接需要登录后才能查看。链接有效期为 7 天。</p>
              <div className="share-dialog-actions">
                <button className="btn-danger" onClick={handleRevokeShare}><Trash2 size={14} /> 撤销分享</button>
              </div>
            </div>
          )}
          {!shareDialog.loading && !shareDialog.error && shareDialog.data?.shared === false && (
            <div className="share-dialog-body">
              <div className="state-empty"><p>分享已过期或已被撤销</p></div>
              <button className="btn-primary full-width" onClick={openShareDialog}>重新生成分享链接</button>
            </div>
          )}
        </div>
      </div>
    )}
  </main>;
}
