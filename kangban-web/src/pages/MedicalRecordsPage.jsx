import { useState, useRef, useEffect, useCallback } from 'react';
import { Bot, Copy, FilePlus2, Filter, Printer, Share2, Sparkles, UploadCloud, Trash2, X, Loader2 } from 'lucide-react';
import * as recordsApi from '../api/medicalRecords.js';
import { API_CONFIG, AUTH_TOKEN_KEY } from '../api/config.js';
import { useAsync } from '../hooks/useAsync.js';
import { StateBoundary } from '../hooks/StateBoundary.jsx';
import { Button, Card, SectionHeading, StatusChip } from '../components/UI.jsx';
import FileList from '../components/FileList.jsx';
import Timeline from '../components/Timeline.jsx';
import { safeParseJson } from './recordDetailData.js';

const ALLOWED_EXTENSIONS = '.pdf,.jpg,.jpeg,.png';

function mapFileItem(item) {
  return {
    id: item.id,
    name: item.recordName || item.name || item.fileName || '未命名文件',
    meta: item.recordDate || item.date || item.createdAt || item.uploadedAt || '',
    tone: item.status === 'completed' ? 'green'
      : item.status === 'processing' || item.status === 'analyzing' ? 'blue'
      : item.status === 'failed' ? 'coral'
      : 'gray',
  };
}

function buildTimelineFromRecord(record, recordsList) {
  if (record?.timelineItems) {
    return record.timelineItems;
  }
  if (recordsList?.length > 0) {
    return recordsList.map((r) => ({
      id: r.id,
      date: r.recordDate || r.date || r.createdAt || '',
      label: r.recordName || r.name || r.fileName || '记录',
    }));
  }
  return [];
}

export default function MedicalRecordsPage({ onNavigate }) {
  const fileInputRef = useRef(null);
  const [selectedId, setSelectedId] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadError, setUploadError] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [shareDialog, setShareDialog] = useState({ open: false, id: null, data: null, loading: false, error: null });
  const [shareCopied, setShareCopied] = useState(false);

  // Fetch records list
  const fetchRecords = useCallback(() => recordsApi.getMedicalRecords(), []);
  const records = useAsync(fetchRecords, { immediate: true });

  // Fetch selected record detail
  const fetchDetail = useCallback(
    (id) => (id ? recordsApi.getMedicalRecord(id) : Promise.resolve(null)),
    [],
  );
  const detail = useAsync(fetchDetail, { immediate: false });

  // Auto-select first record when list loads
  useEffect(() => {
    if (records.data && records.data.length > 0 && !selectedId) {
      setSelectedId(records.data[0].id);
    }
  }, [records.data]);

  // Fetch detail when selection changes
  useEffect(() => {
    if (selectedId) {
      detail.execute(selectedId);
    }
  }, [selectedId]);

  // --- File validation ---
  const validateFile = (file) => {
    const ext = '.' + file.name.split('.').pop().toLowerCase();
    if (!ALLOWED_EXTENSIONS.includes(ext)) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: '仅支持 PDF、JPG、PNG 格式' }));
      return false;
    }
    if (file.size > API_CONFIG.UPLOAD_MAX_SIZE) {
      const maxMB = Math.round(API_CONFIG.UPLOAD_MAX_SIZE / (1024 * 1024));
      window.dispatchEvent(new CustomEvent('app:error', { detail: `文件大小不能超过 ${maxMB}MB` }));
      return false;
    }
    return true;
  };

  // --- Upload with progress ---
  const uploadFile = (file) => {
    setUploading(true);
    setUploadProgress(0);
    setUploadError(null);

    const fd = new FormData();
    fd.append('file', file);

    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    const url = `${API_CONFIG.BASE_URL}/medical-records/upload`;
    const xhr = new XMLHttpRequest();

    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        setUploadProgress(Math.round((e.loaded / e.total) * 100));
      }
    });

    xhr.addEventListener('load', () => {
      setUploading(false);
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const data = JSON.parse(xhr.responseText);
          if (data?.code !== undefined && data.code !== 0) {
            const msg = data.message || data.msg || '上传失败';
            setUploadError(msg);
            window.dispatchEvent(new CustomEvent('app:error', { detail: msg }));
            return;
          }
          window.dispatchEvent(new CustomEvent('app:success', { detail: '记录上传成功' }));
          records.refresh();
        } catch {
          window.dispatchEvent(new CustomEvent('app:success', { detail: '记录上传成功' }));
          records.refresh();
        }
      } else if (xhr.status === 401) {
        window.dispatchEvent(new CustomEvent('auth:unauthorized'));
        setUploadError('登录已失效');
      } else {
        let msg = '上传失败';
        try {
          const data = JSON.parse(xhr.responseText);
          msg = data?.message || data?.msg || msg;
        } catch { /* Keep the generic fallback for non-JSON responses. */ }
        setUploadError(msg);
        window.dispatchEvent(new CustomEvent('app:error', { detail: msg }));
      }
      if (fileInputRef.current) fileInputRef.current.value = '';
    });

    xhr.addEventListener('error', () => {
      setUploading(false);
      const msg = '网络错误，上传失败';
      setUploadError(msg);
      window.dispatchEvent(new CustomEvent('app:error', { detail: msg }));
      if (fileInputRef.current) fileInputRef.current.value = '';
    });

    xhr.addEventListener('abort', () => {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    });

    xhr.open('POST', url);
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }
    xhr.send(fd);
  };

  const handleFileSelect = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (validateFile(file)) {
      uploadFile(file);
    } else {
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  // --- Delete ---
  const handleDelete = (id) => {
    if (!window.confirm('确定要删除这条记录吗？')) return;
    setDeleting(true);
    recordsApi
      .deleteMedicalRecord(id)
      .then(() => {
        window.dispatchEvent(new CustomEvent('app:success', { detail: '记录已删除' }));
        if (selectedId === id) {
          setSelectedId(null);
        }
        records.refresh();
      })
      .catch((err) => {
        window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '删除失败' }));
      })
      .finally(() => {
        setDeleting(false);
      });
  };

  // --- Share ---
  const openShareDialog = async (id) => {
    setShareDialog({ open: true, id, data: null, loading: true, error: null });
    setShareCopied(false);
    try {
      // First get share status to see if already shared
      const status = await recordsApi.getShareStatus(id);
      if (status?.shared) {
        setShareDialog({ open: true, id, data: status, loading: false, error: null });
      } else {
        // Create new share
        const result = await recordsApi.shareMedicalRecord(id);
        setShareDialog({ open: true, id, data: result, loading: false, error: null });
      }
    } catch (err) {
      setShareDialog({ open: true, id, data: null, loading: false, error: err.message || '分享失败' });
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
    if (!shareDialog.id) return;
    try {
      await recordsApi.revokeShare(shareDialog.id);
      setShareDialog({ open: false, id: null, data: null, loading: false, error: null });
      window.dispatchEvent(new CustomEvent('app:success', { detail: '分享已撤销' }));
    } catch (err) {
      window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '撤销失败' }));
    }
  };

  const closeShareDialog = () => {
    setShareDialog({ open: false, id: null, data: null, loading: false, error: null });
    setShareCopied(false);
  };

  // --- Print (download PDF) ---
  const handlePrint = (id) => {
    recordsApi
      .downloadPdf(id, false)
      .then(() => {
        window.dispatchEvent(new CustomEvent('app:success', { detail: 'PDF 已开始下载' }));
      })
      .catch((err) => {
        window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '下载失败' }));
      });
  };

  const handlePrintWithAnalysis = (id) => {
    recordsApi
      .downloadPdf(id, true)
      .then(() => {
        window.dispatchEvent(new CustomEvent('app:success', { detail: 'PDF（含AI分析）已开始下载' }));
      })
      .catch((err) => {
        window.dispatchEvent(new CustomEvent('app:error', { detail: err.message || '下载失败' }));
      });
  };

  const formatExpiry = (expiresAt) => {
    if (!expiresAt) return '';
    const d = new Date(expiresAt);
    return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
  };

  // --- Derived data ---
  const fileList = records.data ? records.data.map(mapFileItem) : [];
  const selectedRecord = detail.data;
  const mappedSelected = selectedRecord ? mapFileItem(selectedRecord) : null;
  const timelineItems = buildTimelineFromRecord(selectedRecord, records.data);
  const diagnosis = safeParseJson(selectedRecord?.diagnosisData);
  const analysisSummary = selectedRecord?.analysis?.summary
    || selectedRecord?.summary
    || diagnosis?.findings
    || diagnosis?.['检查所见']
    || diagnosis?.diagnosis
    || diagnosis?.['诊断结论']
    || '';
  const analysisValues = selectedRecord?.analysis?.values
    || selectedRecord?.values
    || (selectedRecord?.confidence != null
      ? [{ label: 'OCR 置信度', value: `${selectedRecord.confidence}%`, tone: 'blue', status: '已识别' }]
      : []);

  const maxMB = Math.round(API_CONFIG.UPLOAD_MAX_SIZE / (1024 * 1024));

  return (
    <main className="page-content">
      <div className="page-header">
        <div>
          <div className="page-eyebrow">健康档案</div>
          <h1>病历管理</h1>
          <p className="page-subtitle">集中查看和管理您的医疗记录。</p>
        </div>
        <div className="header-actions">
          <input
            ref={fileInputRef}
            type="file"
            accept={ALLOWED_EXTENSIONS}
            style={{ display: 'none' }}
            onChange={handleFileSelect}
            disabled={uploading}
          />
          <Button
            variant="primary"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
          >
            {uploading ? <Loader2 size={14} className="spin" /> : <FilePlus2 size={14} />}
            {uploading ? `上传中 ${uploadProgress}%` : '上传记录'}
          </Button>
        </div>
      </div>

      <div className="records-layout">
        {/* ---- Left sidebar ---- */}
        <aside className="records-left">
          <Card className="upload-card">
            <span className="upload-icon">
              {uploading ? <Loader2 size={17} className="spin" /> : <UploadCloud size={17} />}
            </span>
            <h3>上传新记录</h3>
            <p>
              PDF、JPG 或 PNG 文件均支持<br />
              最大支持 {maxMB}MB
            </p>
            {uploading && uploadProgress > 0 && (
              <div className="upload-progress-bar">
                <div
                  className="upload-progress-fill"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            )}
            {uploadError && <p className="upload-error">{uploadError}</p>}
            <button onClick={() => fileInputRef.current?.click()} disabled={uploading}>
              {uploading ? '上传中...' : '选择文件'}
            </button>
          </Card>

          <Card className="files-card">
            <SectionHeading
              title="近期文件"
              action={
                <button className="more-button" aria-label="文件筛选">
                  <Filter size={14} />
                </button>
              }
            />
            <StateBoundary
              loading={records.loading}
              error={records.error}
              empty={records.empty}
            >
              <FileList files={fileList} selectedId={selectedId} onSelect={setSelectedId} />
            </StateBoundary>
          </Card>
        </aside>

        {/* ---- Right main area ---- */}
        <div className="records-main">
          <StateBoundary
            loading={detail.loading}
            error={detail.error}
            empty={detail.loading ? false : detail.empty}
            loadingRender={
              <Card className="document-card">
                <div className="state-loading">
                  <div className="state-spinner" />
                  <p>加载中...</p>
                </div>
              </Card>
            }
            errorRender={
              <Card className="document-card">
                <div className="state-error">
                  <p>{detail.error}</p>
                </div>
              </Card>
            }
            emptyRender={
              <Card className="document-card">
                <div className="state-empty">
                  <p>请选择一条记录查看详情</p>
                </div>
              </Card>
            }
          >
            {/* Document preview */}
            <Card className="document-card">
              <SectionHeading
                title={mappedSelected?.name || '未命名文件'}
                note={mappedSelected?.meta || ''}
              />
              <div className="document-paper">
                <div className="paper-title" />
                <div className="paper-line" />
                <div className="paper-line short" />
                <div className="paper-grid">
                  <div className="paper-block" />
                  <div className="paper-block coral" />
                </div>
                <div className="paper-line" />
                <div className="paper-line short" />
                <div className="paper-grid">
                  <div className="paper-block coral" />
                  <div className="paper-block" />
                </div>
              </div>
            </Card>

            {/* AI analysis */}
            <Card className="analysis-card">
              <div className="section-heading">
                <h3>
                  <Sparkles size={13} color="var(--primary)" /> AI 分析摘要
                  <span style={{fontSize:10,color:'var(--muted)',fontStyle:'italic',marginLeft:6}}>演示模式</span>
                </h3>
                <div className="header-actions">
                  <button
                    className="more-button"
                    aria-label="打印报告"
                    onClick={() => handlePrint(selectedId)}
                  >
                    <Printer size={13} />
                  </button>
                  <button
                    className="more-button"
                    aria-label="分享报告"
                    onClick={() => openShareDialog(selectedId)}
                  >
                    <Share2 size={13} />
                  </button>
                  <button
                    className="more-button"
                    aria-label="删除记录"
                    onClick={() => handleDelete(selectedId)}
                    disabled={deleting}
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
              <p>{analysisSummary || '暂无分析摘要'}</p>
              <div className="analysis-values">
                {analysisValues.length > 0
                  ? analysisValues.map((v, i) => (
                      <div className="analysis-value" key={v.label || i}>
                        <small>{v.label}</small>
                        <strong>
                          {v.value}
                          {v.unit && <small> {v.unit}</small>}
                        </strong>
                        <StatusChip tone={v.tone || 'blue'}>
                          {v.status || v.chip}
                        </StatusChip>
                      </div>
                    ))
                  : <p className="state-empty">暂无可展示的分析指标</p>}
              </div>
              <Button
                variant="soft"
                className="full-width"
                onClick={() => onNavigate('record-detail', { id: selectedId })}
              >
                <Bot size={13} />
                查看完整分析
              </Button>
            </Card>

            {/* Timeline */}
            <Card className="records-timeline">
              <SectionHeading
                title="患者病历时间轴"
                action={<button className="more-button">查看完整记录</button>}
              />
              {timelineItems.length > 0
                ? <Timeline items={timelineItems} activeId={selectedId} />
                : <p className="state-empty">暂无时间线记录</p>}
            </Card>
          </StateBoundary>
        </div>
      </div>

      {/* ---- Share Dialog ---- */}
      {shareDialog.open && (
        <div className="confirm-dialog" onClick={closeShareDialog}>
          <div className="confirm-dialog-card share-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="share-dialog-header">
              <h3><Share2 size={16} /> 分享病历</h3>
              <button className="share-dialog-close" onClick={closeShareDialog}><X size={16} /></button>
            </div>

            {shareDialog.loading && (
              <div className="state-loading">
                <div className="state-spinner" />
                <p>生成分享链接...</p>
              </div>
            )}

            {shareDialog.error && (
              <div className="state-error">
                <p>{shareDialog.error}</p>
                <button className="btn-cancel" onClick={closeShareDialog}>关闭</button>
              </div>
            )}

            {!shareDialog.loading && !shareDialog.error && shareDialog.data?.shared !== false && (
              <div className="share-dialog-body">
                <div className="share-link-field">
                  <label>分享链接</label>
                  <div className="share-link-input-group">
                    <input
                      type="text"
                      readOnly
                      value={shareDialog.data?.shareUrl || ''}
                      onClick={(e) => e.target.select()}
                    />
                    <button
                      className="btn-primary"
                      onClick={handleCopyShareLink}
                      title="复制链接"
                    >
                      <Copy size={14} />
                      {shareCopied ? '已复制' : '复制'}
                    </button>
                  </div>
                </div>

                {shareDialog.data?.expiresAt && (
                  <p className="share-expiry">
                    有效期至：{formatExpiry(shareDialog.data.expiresAt)}
                  </p>
                )}

                <p className="share-hint">
                  分享链接需要登录后才能查看。链接有效期为 7 天。
                </p>

                <div className="share-dialog-actions">
                  <button className="btn-danger" onClick={handleRevokeShare}>
                    <Trash2 size={14} /> 撤销分享
                  </button>
                </div>
              </div>
            )}

            {!shareDialog.loading && !shareDialog.error && shareDialog.data?.shared === false && (
              <div className="share-dialog-body">
                <div className="state-empty">
                  <p>分享已过期或已被撤销</p>
                </div>
                <button
                  className="btn-primary full-width"
                  onClick={async () => {
                    setShareDialog((prev) => ({ ...prev, loading: true, error: null }));
                    try {
                      const result = await recordsApi.shareMedicalRecord(shareDialog.id);
                      setShareDialog((prev) => ({ ...prev, data: result, loading: false }));
                    } catch (err) {
                      setShareDialog((prev) => ({ ...prev, loading: false, error: err.message || '重新分享失败' }));
                    }
                  }}
                >
                  重新生成分享链接
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </main>
  );
}
