import { useEffect, useMemo, useState } from 'react';
import { BookOpen, Check, ChevronDown, Eye, KeyRound, LoaderCircle, RefreshCw, Search, UploadCloud, X } from 'lucide-react';
import * as knowledgeApi from '../api/knowledge.js';
import { Button, Card } from '../components/UI.jsx';

const STATUS_LABELS = {
  DRAFT: '草稿', PENDING_REVIEW: '待审核', PUBLISHED: '已发布', REVOKED: '已撤回', EXPIRED: '已过期',
};

function statusLabel(value) { return STATUS_LABELS[value] || value || '未知'; }

export default function KnowledgeAdminPage() {
  const [token, setToken] = useState('');
  const [documents, setDocuments] = useState([]);
  const [title, setTitle] = useState('');
  const [source, setSource] = useState('官方资料');
  const [file, setFile] = useState(null);
  const [query, setQuery] = useState('');
  const [searchResult, setSearchResult] = useState(null);
  const [searchError, setSearchError] = useState('');
  const [chunks, setChunks] = useState(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);

  const counts = useMemo(() => documents.reduce((result, document) => {
    result[document.status] = (result[document.status] || 0) + 1;
    return result;
  }, {}), [documents]);

  const loadDocuments = async () => {
    if (!token.trim()) return;
    setLoading(true);
    try {
      setDocuments(await knowledgeApi.getKnowledgeDocuments(token.trim()) || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (token.trim()) loadDocuments();
  }, []);

  const runAction = async (action, successMessage) => {
    setBusy(true);
    try {
      await action();
      window.dispatchEvent(new CustomEvent('app:success', { detail: successMessage }));
      await loadDocuments();
    } finally {
      setBusy(false);
    }
  };

  const handleUpload = async (event) => {
    event.preventDefault();
    if (!file) return;
    await runAction(
      () => knowledgeApi.uploadKnowledgeDocument(token.trim(), file, { title, source }),
      '文档已提交，正在后台解析',
    );
    setFile(null);
    setTitle('');
    event.currentTarget.reset();
  };

  const inspectChunks = async (id) => {
    setChunks(await knowledgeApi.getKnowledgeChunks(token.trim(), id));
  };

  const runSearch = async (event) => {
    event.preventDefault();
    if (!query.trim()) return;
    setSearchError('');
    try {
      setSearchResult(await knowledgeApi.searchKnowledge(token.trim(), query.trim()));
    } catch (error) {
      setSearchResult(null);
      setSearchError(error?.message || '知识库暂时不可用，请稍后重试。');
    }
  };

  return <main className="page-content knowledge-admin-page">
    <header className="account-heading knowledge-admin-heading">
      <div><p className="page-eyebrow">PUBLIC KNOWLEDGE / ADMIN</p><h1>公共知识库</h1><p>上传、审核并管理 Agent 可引用的公共健康资料。</p></div>
      <Button onClick={loadDocuments} disabled={loading || !token.trim()}><RefreshCw size={15} className={loading ? 'spin' : ''} />刷新</Button>
    </header>

    <Card className="knowledge-admin-token">
      <KeyRound size={17} /><div><strong>管理凭据</strong><p>仅在当前页面内存中使用，不会写入浏览器存储。</p></div>
      <input type="password" value={token} onChange={(event) => setToken(event.target.value)} placeholder="输入 X-Knowledge-Admin-Token" autoComplete="off" />
    </Card>

    <section className="knowledge-admin-stats">
      {['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REVOKED'].map(status => <Card key={status}><span>{statusLabel(status)}</span><strong>{counts[status] || 0}</strong></Card>)}
    </section>

    <section className="knowledge-admin-grid">
      <Card className="knowledge-upload-card">
        <div className="knowledge-section-title"><div><BookOpen size={17} /><h2>导入资料</h2></div><small>TXT / Markdown / 文本型 PDF</small></div>
        <form onSubmit={handleUpload}>
          <label>资料标题<input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="例如：家庭血压管理指南" /></label>
          <label>资料来源<input value={source} onChange={(event) => setSource(event.target.value)} placeholder="官方资料 / 医院授权资料" /></label>
          <label className="knowledge-file-input">文件<input type="file" accept=".txt,.md,.markdown,.pdf" onChange={(event) => { setFile(event.target.files?.[0] || null); }} /><span>{file ? file.name : '选择文件，大小不超过 10MB'}</span></label>
          <Button variant="primary" className="full-width" disabled={!file || !token.trim() || busy}><UploadCloud size={16} />{busy ? '提交中...' : '上传并解析'}</Button>
        </form>
        <p className="knowledge-note">扫描版 PDF、图片和 OCR 暂未接入；发布前必须完成审核。</p>
      </Card>

      <Card className="knowledge-search-card">
        <div className="knowledge-section-title"><div><Search size={17} /><h2>检索预览</h2></div><small>仅返回已发布资料</small></div>
        <form className="knowledge-search-form" onSubmit={runSearch}><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="输入血压、用药等关键词" /><Button variant="soft" disabled={!token.trim()}><Search size={15} />检索</Button></form>
        {searchError ? <div className="knowledge-search-error" role="alert">{searchError}</div> : searchResult ? <div className="knowledge-search-result"><p>命中 {searchResult.hits?.length || 0} 条，引用 {searchResult.citations?.length || 0} 个</p><pre>{searchResult.context || '暂无足够依据'}</pre></div> : <div className="knowledge-empty">检索结果会显示在这里，并携带文档和页码引用。</div>}
      </Card>
    </section>

    <Card className="knowledge-documents-card">
      <div className="knowledge-section-title"><div><BookOpen size={17} /><h2>资料列表</h2></div><small>{documents.length} 份资料</small></div>
      {documents.length === 0 ? <div className="knowledge-empty">输入管理凭据后点击刷新。</div> : <div className="knowledge-document-list">{documents.map(document => <article key={document.id} className="knowledge-document-row">
        <div className="knowledge-document-main"><strong>{document.title}</strong><p>{document.source} · {document.file_name}</p><small>状态：{statusLabel(document.status)} · 版本 {document.version}</small></div>
        <div className="knowledge-document-actions">
          <button type="button" onClick={() => inspectChunks(document.id)} disabled={busy}><Eye size={14} />切片</button>
          {document.status === 'DRAFT' && <button type="button" onClick={() => runAction(() => knowledgeApi.submitKnowledgeReview(token.trim(), document.id), '已提交审核')} disabled={busy}><Check size={14} />送审</button>}
          {document.status === 'PENDING_REVIEW' && <button type="button" onClick={() => runAction(() => knowledgeApi.publishKnowledgeDocument(token.trim(), document.id), '已发布')} disabled={busy}><Check size={14} />发布</button>}
          {document.status === 'PUBLISHED' && <button type="button" onClick={() => runAction(() => knowledgeApi.revokeKnowledgeDocument(token.trim(), document.id), '已撤回')} disabled={busy}><X size={14} />撤回</button>}
          <button type="button" onClick={() => runAction(() => knowledgeApi.reindexKnowledgeDocument(token.trim(), document.id), '已提交重建索引')} disabled={busy}><RefreshCw size={14} />重建</button>
        </div>
      </article>)}</div>}
    </Card>

    {chunks && <div className="knowledge-modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setChunks(null)}><section className="knowledge-chunks-modal" role="dialog" aria-modal="true"><header><h2>知识切片预览</h2><button type="button" onClick={() => setChunks(null)} aria-label="关闭"><X size={18} /></button></header>{chunks.map(chunk => <article key={chunk.id}><span>#{chunk.chunk_index} {chunk.page_number ? `第${chunk.page_number}页` : ''}</span><p>{chunk.content}</p></article>)}</section></div>}
  </main>;
}
