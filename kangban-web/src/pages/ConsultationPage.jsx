import { useState, useEffect, useCallback, useRef } from 'react';
import { ChevronDown, Clock3, Edit3, Mic, Paperclip, Send, Sparkles, Stethoscope, Users } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import * as consultApi from '../api/consultation.js';
import * as familyApi from '../api/family.js';
import * as healthApi from '../api/health.js';
import { API_CONFIG, AUTH_TOKEN_KEY } from '../api/config.js';
import { buildConsultationPayload } from '../api/contracts.js';
import { Card, IconButton, StatusChip } from '../components/UI.jsx';
import { getUserDisplayName } from '../data.js';

function hasPersonalizedContext(session, selectedTarget) {
  if (typeof session?.patientData !== 'string') return false;
  try {
    const context = JSON.parse(session.patientData);
    if (context?.contextVersion !== 'family-agent-v2') return false;
    if (!selectedTarget) return context.selectedMemberId == null && session.subjectUserId == null;
    if (selectedTarget.kind === 'account') {
      return Number(session.subjectUserId) === Number(selectedTarget.subjectUserId)
        && context.selectedMemberId == null;
    }
    return Number(context.selectedMemberId) === Number(selectedTarget.memberId);
  } catch {
    return false;
  }
}

export default function ConsultationPage() {
  const { user } = useAuth();
  const [sessionId, setSessionId] = useState(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [streamingText, setStreamingText] = useState('');
  const [thinking, setThinking] = useState(false);
  const [thinkingText, setThinkingText] = useState('');
  const [sendError, setSendError] = useState(null);
  const [healthOverview, setHealthOverview] = useState({});
  const [familyMembers, setFamilyMembers] = useState([]);
  const [selectedMemberId, setSelectedMemberId] = useState(null);
  const [memberMenuOpen, setMemberMenuOpen] = useState(false);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [sessionError, setSessionError] = useState(null);
  const [sessionReloadKey, setSessionReloadKey] = useState(0);
  const [messagesData, setMessagesData] = useState([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messagesError, setMessagesError] = useState(null);
  const [showScrollToBottom, setShowScrollToBottom] = useState(false);
  const chatMessagesRef = useRef(null);
  const shouldFollowMessagesRef = useRef(true);
  const autoScrollTimerRef = useRef(null);
  const autoScrollingRef = useRef(false);
  const eventSourceRef = useRef(null);
  const sendingRef = useRef(false);
  const failedMessageIdRef = useRef(null);
  const pendingSummaryMemberRef = useRef(null);

  useEffect(() => {
    let active = true;
    const selectedTarget = familyMembers.find((member) => String(member.id) === String(selectedMemberId)) || null;
    Promise.all([
      healthApi.getHealthTrends({ metric: 'heart_rate', days: 30, memberId: selectedTarget?.memberId ?? null, subjectUserId: selectedTarget?.subjectUserId ?? null }),
      healthApi.getHealthTrends({ metric: 'blood_pressure', days: 30, memberId: selectedTarget?.memberId ?? null, subjectUserId: selectedTarget?.subjectUserId ?? null }),
    ]).then(([heart, pressure]) => {
      if (active) setHealthOverview({ heart, pressure });
    }).catch(() => {
      if (active) setHealthOverview({});
    });
    return () => { active = false; };
  }, [selectedMemberId, familyMembers]);

  /* Normalise API response: accept array or { messages: […] } */
  const messages = Array.isArray(messagesData)
    ? messagesData
    : messagesData?.messages || [];

  const scrollChatToBottom = useCallback((behavior = 'smooth') => {
    const container = chatMessagesRef.current;
    if (!container) return;
    shouldFollowMessagesRef.current = true;
    autoScrollingRef.current = true;
    setShowScrollToBottom(false);
    container.scrollTo({ top: container.scrollHeight, behavior });
    window.clearTimeout(autoScrollTimerRef.current);
    autoScrollTimerRef.current = window.setTimeout(() => {
      autoScrollingRef.current = false;
    }, behavior === 'smooth' ? 500 : 0);
  }, []);

  const handleChatScroll = useCallback(() => {
    const container = chatMessagesRef.current;
    if (!container || autoScrollingRef.current) return;
    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    const isNearBottom = distanceFromBottom < 72;
    shouldFollowMessagesRef.current = isNearBottom;
    setShowScrollToBottom(!isNearBottom);
  }, []);

  const handleUserScrollIntent = useCallback(() => {
    autoScrollingRef.current = false;
    window.clearTimeout(autoScrollTimerRef.current);
  }, []);

  useEffect(() => () => {
    window.clearTimeout(autoScrollTimerRef.current);
    eventSourceRef.current?.close();
  }, []);

  useEffect(() => {
    if (!shouldFollowMessagesRef.current) return undefined;
    const frame = window.requestAnimationFrame(() => {
      const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      scrollChatToBottom(reduceMotion ? 'auto' : 'smooth');
    });
    return () => window.cancelAnimationFrame(frame);
  }, [messages.length, thinking, thinkingText, streaming, streamingText, sendError, scrollChatToBottom]);

  useEffect(() => {
    let active = true;
    familyApi.getPatientTargets()
      .then((items) => {
        if (active) setFamilyMembers(Array.isArray(items)
          ? items.filter((item) => item.kind !== 'account' || item.permissions?.canUseAi)
          : []);
      })
      .catch(() => {
        if (active) setFamilyMembers([]);
      });
    return () => { active = false; };
  }, []);

  const selectedMember = familyMembers.find((member) => String(member.id) === String(selectedMemberId)) || null;

  /* ---- Sessions are isolated by selected patient ---- */
  useEffect(() => {
    if (!user?.id || (selectedMemberId != null && !selectedMember)) return undefined;
    let active = true;
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    sendingRef.current = false;
    failedMessageIdRef.current = null;
    setSessionId(null);
    setMessagesData([]);
    setMessagesLoading(false);
    setMessagesError(null);
    setSessionLoading(true);
    setSessionError(null);
    setSending(false);
    setThinking(false);
    setStreaming(false);
    setStreamingText('');
    setSendError(null);

    (async () => {
      try {
        const selectedKey = selectedMemberId == null ? 'self' : String(selectedMemberId);
        const shouldAppendSummary = pendingSummaryMemberRef.current === selectedKey;
        const sessions = await consultApi.getChatSessions({
          memberId: selectedMember?.memberId ?? null,
          subjectUserId: selectedMember?.subjectUserId ?? null,
        });
        const existing = Array.isArray(sessions)
          ? sessions.find((session) => (
              session.status === 'active'
              && hasPersonalizedContext(session, selectedMember)
            ))
          : null;
        let sid = existing?.id;
        if (!sid) {
          const created = await consultApi.createSession(
            buildConsultationPayload(user, new Date(), selectedMember),
          );
          sid = created?.id || created?.sessionId;
        }
        if (!sid) throw new Error('问诊会话创建失败');
        if (existing?.id) {
          sid = existing.id;
        }
        if (existing?.id && shouldAppendSummary) {
          await consultApi.appendPatientSummary(sid);
        }
        if (shouldAppendSummary && pendingSummaryMemberRef.current === selectedKey) {
          pendingSummaryMemberRef.current = null;
        }
        if (active) {
          setSessionId(sid);
          setMessagesLoading(true);
          const loadedMessages = await consultApi.getChatMessages(sid);
          if (active) setMessagesData(loadedMessages || []);
        }
      } catch (error) {
        if (active) {
          setSessionError(error?.message || '问诊会话创建失败');
          setMessagesError(error?.message || '消息加载失败');
        }
      } finally {
        if (active) {
          setSessionLoading(false);
          setMessagesLoading(false);
        }
      }
    })();
    return () => { active = false; };
  }, [user?.id, selectedMemberId, selectedMember, sessionReloadKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleMemberChange = useCallback((nextMemberId) => {
    if (nextMemberId === selectedMemberId) {
      setMemberMenuOpen(false);
      return;
    }
    const nextMember = familyMembers.find((member) => String(member.id) === String(nextMemberId));
    const nextName = nextMember?.name || '本人';
    const confirmed = window.confirm(
      `即将切换至${nextName}的独立问诊，本次对话不会带入新会话。是否继续？`,
    );
    if (!confirmed) return;
    pendingSummaryMemberRef.current = nextMemberId == null ? 'self' : String(nextMemberId);
    setMemberMenuOpen(false);
    setSelectedMemberId(nextMemberId);
  }, [familyMembers, selectedMemberId]);

  const openResponseStream = useCallback((messageId) => {
    if (!sessionId || !messageId) return;

    eventSourceRef.current?.close();
    sendingRef.current = true;
    failedMessageIdRef.current = messageId;
    setSending(true);
    setSendError(null);
    setStreaming(false);
    setThinking(true);
    setThinkingText('');
    setStreamingText('');

    const token = localStorage.getItem(AUTH_TOKEN_KEY);
    const params = new URLSearchParams({ messageId: String(messageId) });
    const url = `${API_CONFIG.BASE_URL}/consultation/sessions/${sessionId}/stream?${params}`;
    const controller = new AbortController();
    const streamConnection = { close: () => controller.abort() };
    eventSourceRef.current = streamConnection;
    let settled = false;

    const finish = () => {
      settled = true;
      streamConnection.close();
      if (eventSourceRef.current === streamConnection) eventSourceRef.current = null;
      sendingRef.current = false;
      setThinking(false);
      setStreaming(false);
      setSending(false);
    };

    const handleStreamEvent = (eventName, data) => {
      if (eventName === 'thinking') {
        setThinkingText(data);
        return;
      }
      if (eventName === 'thinking_done') {
        setThinking(false);
        setStreaming(true);
        return;
      }
      if (eventName === 'token') {
        setStreamingText(data);
        return;
      }
      if (eventName === 'ai_error') {
        if (settled) return;
        finish();
        setSendError(data || 'AI 服务暂时不可用，请稍后重试');
        return;
      }
      if (eventName !== 'done') return;
      if (settled) return;
      const finalText = data;
      finish();
      failedMessageIdRef.current = null;
      setMessagesData((previous) => {
        const list = Array.isArray(previous) ? previous : (previous?.messages || []);
        if (list.some((message) => message.replyToMessageId === messageId)) return list;
        return [...list, {
          id: `assistant-${messageId}`,
          role: 'assistant',
          content: finalText,
          replyToMessageId: messageId,
          createdAt: new Date().toISOString(),
        }];
      });
    };

    const consumeBlock = (block) => {
      let eventName = 'message';
      const dataLines = [];
      block.split('\n').forEach((line) => {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
      });
      handleStreamEvent(eventName, dataLines.join('\n'));
    };

    const readStream = async () => {
      try {
        if (!token) throw new Error('登录状态已失效，请重新登录');
        const response = await fetch(url, {
          headers: {
            Accept: 'text/event-stream',
            Authorization: `Bearer ${token}`,
          },
          signal: controller.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error(response.status === 401 || response.status === 403
            ? '登录状态已失效，请重新登录'
            : 'AI 连接建立失败，请重试');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (!settled) {
          const { value, done } = await reader.read();
          buffer = (buffer + decoder.decode(value || new Uint8Array(), { stream: !done }))
            .replace(/\r\n/g, '\n');
          let boundary = buffer.indexOf('\n\n');
          while (boundary >= 0 && !settled) {
            consumeBlock(buffer.slice(0, boundary));
            buffer = buffer.slice(boundary + 2);
            boundary = buffer.indexOf('\n\n');
          }
          if (done) break;
        }
        if (!settled) throw new Error('AI 连接意外中断，请重试');
      } catch (error) {
        if (settled || error?.name === 'AbortError') return;
        finish();
        setSendError(error?.message || '连接中断，请重试');
      }
    };

    readStream();
  }, [sessionId, setMessagesData]);

  const retryLastResponse = useCallback(() => {
    if (!failedMessageIdRef.current || sendingRef.current) return;
    openResponseStream(failedMessageIdRef.current);
  }, [openResponseStream]);

  /* ---- send message (SSE streaming) ---- */
  const sendMessage = useCallback(
    async (content) => {
      const text = (content ?? draft).trim();
      if (!text || !sessionId || sendingRef.current) return;

      const clientMessageId = globalThis.crypto?.randomUUID?.()
        || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      const optimisticId = `pending-${clientMessageId}`;
      sendingRef.current = true;
      setSending(true);
      setSendError(null);
      setDraft('');
      shouldFollowMessagesRef.current = true;

      setMessagesData((previous) => {
        const list = Array.isArray(previous) ? previous : (previous?.messages || []);
        return [...list, {
          id: optimisticId,
          role: 'user',
          content: text,
          clientMessageId,
          createdAt: new Date().toISOString(),
        }];
      });

      try {
        const result = await consultApi.sendMessage(
          sessionId,
          text,
          clientMessageId,
          { skipLoading: true },
        );
        const userMessage = result?.userMessage;
        if (!userMessage?.id) throw new Error('消息保存失败，请重试');

        setMessagesData((previous) => {
          const list = Array.isArray(previous) ? previous : (previous?.messages || []);
          return list.map((message) => message.id === optimisticId ? userMessage : message);
        });
        openResponseStream(userMessage.id);
      } catch (err) {
        sendingRef.current = false;
        setSending(false);
        setThinking(false);
        setStreaming(false);
        setMessagesData((previous) => {
          const list = Array.isArray(previous) ? previous : (previous?.messages || []);
          return list.filter((message) => message.id !== optimisticId);
        });
        setSendError(err?.message || '发送失败，请重试');
      }
    },
    [draft, sessionId, setMessagesData, openResponseStream],
  );

  /* ---- view consultation history ---- */
  const handleFetchHistory = useCallback(async () => {
    try {
      const data = await consultApi.getConsultationHistory();
      window.dispatchEvent(
        new CustomEvent('feedback', {
          detail: { type: 'history', message: '问诊历史', data },
        }),
      );
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent('feedback', {
          detail: { type: 'history-error', message: '获取问诊历史失败' },
        }),
      );
    }
  }, []);

  /* ---- edit patient info feedback ---- */
  const handleEditPatient = useCallback(() => {
    window.dispatchEvent(
      new CustomEvent('feedback', {
        detail: { type: 'edit-patient', message: '编辑患者信息' },
      }),
    );
  }, []);

  /* ---- attachment feedback ---- */
  const handleAttachment = useCallback(() => {
    window.dispatchEvent(
      new CustomEvent('feedback', {
        detail: { type: 'attachment', message: '添加附件功能即将上线' },
      }),
    );
  }, []);

  /* ---- voice input feedback ---- */
  const handleVoice = useCallback(() => {
    window.dispatchEvent(
      new CustomEvent('feedback', {
        detail: { type: 'voice', message: '语音输入功能即将上线' },
      }),
    );
  }, []);

  /* ---- derive patient display from selected patient ---- */
  const patientDisplay = {
    name: selectedMember?.name || getUserDisplayName(user, '未知患者'),
    age: (selectedMember?.age ?? user?.age) != null ? `${selectedMember?.age ?? user?.age} 岁` : '--',
    gender: selectedMember?.gender || user?.gender || '--',
    relation: selectedMember?.relation || '本人',
    detail: selectedMember ? (selectedMember.note || '暂无备注') : (user?.email || '未验证'),
    patientId: selectedMember ? `家庭成员 #${selectedMember.id}` : (user?.patientId || '本人档案'),
    avatar: selectedMember ? selectedMember.avatarUrl : (user?.avatarUrl || null),
    initial: (selectedMember?.name || user?.name || user?.username || 'AI')[0],
  };

  const DocIcon = ({ size = 34 }) => (
    <span style={{ fontSize: size - 6, lineHeight: 1, flexShrink: 0 }}>🧑‍⚕️</span>
  );
  const UserIcon = ({ size = 34 }) => (
    <span style={{ fontSize: size - 6, lineHeight: 1, flexShrink: 0 }}>🙋</span>
  );
  const latestHeart = healthOverview.heart?.records?.[0];
  const latestPressure = healthOverview.pressure?.records?.[0];
  const healthRecordCount = (healthOverview.heart?.records?.length || 0) + (healthOverview.pressure?.records?.length || 0);

  /* ---- loading/error state helpers ---- */
  const isLoadingInitial = (sessionLoading || (sessionId && messagesLoading)) && messages.length === 0 && !thinking && !streaming;
  const loadError = sessionError || messagesError;

  /** Render a single message bubble */
  const renderMessage = (msg) => {
    const role = msg.role || msg.sender || 'user';
    const text = msg.text || msg.content || '';
    const id = msg.id || msg._id || Math.random();
    const isAssistant = role === 'assistant' || role === 'ai' || role === 'model';

    return isAssistant ? (
      <div key={id} className="message" style={{alignItems:'flex-start'}}>
        <DocIcon />
        <div>
          <div className="message-bubble">{text}</div>
        </div>
      </div>
    ) : (
      <div key={id} className="message user">
        <UserIcon />
        <div className="message-bubble">{text}</div>
      </div>
    );
  };

  /** Content of the chat-messages area — never hides existing content */
  const renderChatContent = () => {
    const hasContent = messages.length > 0 || thinking || streaming;
    if (!hasContent && loadError) {
      return <div className="state-error"><p>{loadError}</p></div>;
    }
    if (!hasContent) {
      return <div className="state-empty"><p>{sessionId ? '暂无消息，开始您的问诊吧' : '正在创建会话...'}</p></div>;
    }
    return (
      <>
        {messages.map(renderMessage)}
        {thinking && (
          <div className="message" style={{alignItems:'flex-start'}}>
            <DocIcon />
            <div>
              <div className="message-bubble thinking" style={{opacity:0.7,fontStyle:'italic'}}>
                {thinkingText || '正在分析...'}<span className="cursor-blink">|</span>
              </div>
            </div>
          </div>
        )}
        {streaming && streamingText && (
          <div className="message" style={{alignItems:'flex-start'}}>
            <DocIcon />
            <div>
              <div className="message-bubble streaming">{streamingText}<span className="cursor-blink">|</span></div>
            </div>
          </div>
        )}
      </>
    );
  };

  return (
    <main className="page-content">
      <div className="consultation-layout">
        {/* ========== Left sidebar ========== */}
        <aside className="consultation-side">
          {/* Patient info card */}
          <Card className="patient-card">
            <div className="patient-card-head">
              <span className="patient-avatar" aria-hidden="true">
                {patientDisplay.avatar
                  ? <img src={patientDisplay.avatar} alt="" />
                  : patientDisplay.initial}
              </span>
              <span className="patient-heading-copy">
                <h3>患者信息</h3>
                <p>已同步健康档案</p>
              </span>
              <button
                className="more-button"
                aria-label="编辑患者信息"
                onClick={handleEditPatient}
              >
                <Edit3 size={14} />
              </button>
            </div>
            <div className="patient-switcher-wrap">
              <button
                type="button"
                className="patient-switcher"
                aria-label="切换问诊患者"
                aria-expanded={memberMenuOpen}
                onClick={() => setMemberMenuOpen((open) => !open)}
              >
                <Users size={14} />
                <span>
                  <small>当前问诊对象</small>
                  <strong>{patientDisplay.name} · {patientDisplay.relation}</strong>
                </span>
                <ChevronDown size={14} />
              </button>
              {memberMenuOpen && (
                <div className="patient-switcher-menu" role="menu" aria-label="选择问诊患者">
                  <button
                    type="button"
                    role="menuitem"
                    className={selectedMemberId == null ? 'selected' : ''}
                    onClick={() => handleMemberChange(null)}
                  >
                    <span>我</span>
                    <strong>{getUserDisplayName(user, '本人')}</strong>
                    <small>本人</small>
                  </button>
                  {familyMembers.map((member) => (
                    <button
                      type="button"
                      role="menuitem"
                      key={member.id}
                      className={String(selectedMemberId) === String(member.id) ? 'selected' : ''}
                      onClick={() => handleMemberChange(member.id)}
                    >
                      <span>{member.name?.[0] || '家'}</span>
                      <strong>{member.name}</strong>
                      <small>{member.kind === 'account' ? `${member.relation || '家庭成员'} · 共享账号` : (member.relation || '家庭成员')}</small>
                    </button>
                  ))}
                </div>
              )}
            </div>
            <div className="patient-fields">
              <div className="patient-field">
                <small>姓名</small>
                <strong>{patientDisplay.name}</strong>
              </div>
              <div className="patient-field">
                <small>年龄</small>
                <strong>{patientDisplay.age}</strong>
              </div>
              <div className="patient-field">
                <small>性别</small>
                <strong>{patientDisplay.gender}</strong>
              </div>
              <div className="patient-field">
                <small>{selectedMember ? '关系' : '邮箱'}</small>
                <strong>{selectedMember ? patientDisplay.relation : patientDisplay.detail}</strong>
              </div>
              <div className="patient-field full">
                <small>健康档案编号</small>
                <strong>{patientDisplay.patientId}</strong>
              </div>
            </div>
          </Card>

          {/* Health overview card */}
          <Card className="overview-card">
            <div className="section-heading">
              <h2>健康概览</h2>
              <button className="more-button" aria-label="收起概览">
                <ChevronDown size={14} />
              </button>
            </div>
            <div className="overview-list">
              <div className="overview-item">
                <span>最新心率</span>
                <strong>{latestHeart ? `${latestHeart.value} ${latestHeart.unit || 'bpm'}` : '暂无记录'}</strong>
              </div>
              <div className="overview-item">
                <span>血压</span>
                <strong>{latestPressure ? `${latestPressure.value} ${latestPressure.unit || 'mmHg'}` : '暂无记录'}</strong>
              </div>
            </div>
            <StatusChip tone="blue">近 30 天 · {healthRecordCount ? `已记录 ${healthRecordCount} 条` : '暂无健康数据'}</StatusChip>
          </Card>
        </aside>

        {/* ========== Chat area ========== */}
        <Card className="chat-card">
          <header className="chat-header">
            <div>
              <h2>{patientDisplay.name}的问诊助手</h2>
              <div className="chat-status">
                {thinking ? (
                  <>
                    <span className="dot processing" style={{background:'#f59e0b',animation:'fadeIn 0.6s infinite alternate'}} />
                    💭 深度思考中...
                  </>
                ) : streaming ? (
                  <>
                    <span className="dot processing" style={{background:'var(--primary)',animation:'fadeIn 0.6s infinite alternate'}} />
                    ✍️ AI 正在输入...
                  </>
                ) : sending ? (
                  <>
                    <span className="dot processing" style={{background:'var(--primary)',animation:'fadeIn 0.6s infinite alternate'}} />
                    AI 正在分析中...
                  </>
                ) : sendError ? (
                  <>
                    <span className="dot error" style={{background:'var(--primary)'}} />
                    {sendError.includes('重试') ? sendError : 'AI 服务暂时不可用'}
                    <button className="retry-link" onClick={retryLastResponse}
                      style={{marginLeft:8,fontSize:12,color:'var(--primary)',cursor:'pointer',background:'none',border:'none',textDecoration:'underline'}}>重试</button>
                  </>
                ) : (
                  <>
                    <span className="dot" />
                    AI 已连接 · <span style={{color:'var(--muted)',marginLeft:4}}>个性化健康档案</span>
                  </>
                )}
              </div>
            </div>
            <div className="header-actions">
              <IconButton label="查看问诊历史" onClick={handleFetchHistory}>
                <Clock3 size={14} />
              </IconButton>
              <IconButton label="更多问诊操作">
                <Stethoscope size={14} />
              </IconButton>
            </div>
          </header>

          <div
            ref={chatMessagesRef}
            className="chat-messages"
            onScroll={handleChatScroll}
            onWheel={handleUserScrollIntent}
            onTouchStart={handleUserScrollIntent}
          >
            <div className="date-divider">今天，12:42 AM</div>
            {renderChatContent()}
            {sendError && (
              <div className="state-error" style={{ padding: '8px 20px' }}>
                <p>{sendError}</p>
              </div>
            )}
          </div>
          {showScrollToBottom && (
            <button
              type="button"
              className="chat-scroll-button"
              aria-label="回到最新消息"
              onClick={() => scrollChatToBottom('smooth')}
            >
              <ChevronDown size={16} />
            </button>
          )}

          {/* Quick replies */}
          <div className="quick-replies">
            <button
              className="quick-reply"
              onClick={() => sendMessage('我还想了解血压变化')}
              disabled={sending || !sessionId}
            >
              我还想了解血压变化
            </button>
            <button
              className="quick-reply"
              onClick={() => sendMessage('没有了，我现在感觉好多了')}
              disabled={sending || !sessionId}
            >
              没有了，我现在感觉好多了
            </button>
            <button
              className="quick-reply"
              onClick={() => sendMessage('记录这条健康信息')}
              disabled={sending || !sessionId}
            >
              记录这条健康信息
            </button>
          </div>

          {/* Composer */}
          <div className="composer">
            <button
              className="more-button"
              aria-label="添加附件"
              onClick={handleAttachment}
            >
              <Paperclip size={16} />
            </button>
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !sending && sessionId) sendMessage();
              }}
              placeholder={sessionId ? "输入您的信息或症状描述..." : "正在创建会话..."}
              aria-label="输入问诊内容"
              disabled={sending || !sessionId}
            />
            <button
              className="more-button"
              aria-label="语音输入"
              onClick={handleVoice}
            >
              <Mic size={16} />
            </button>
            <button
              className="send-button"
              onClick={() => sendMessage()}
              aria-label="发送消息"
              disabled={sending || !sessionId || !draft.trim()}
            >
              <Send size={15} />
            </button>
          </div>
          {!sessionId && !sessionLoading && (
            <div className="state-error" style={{padding:'8px 20px',fontSize:12}}>
              <p>会话创建失败，请 <button onClick={() => setSessionReloadKey((key) => key + 1)} style={{color:'var(--primary)',cursor:'pointer',background:'none',border:'none',textDecoration:'underline'}}>点击重试</button></p>
            </div>
          )}

          <div className="chat-disclaimer">
            <Sparkles size={10} />
            🏥 当前分析仅使用{patientDisplay.name}的授权健康数据，AI 建议不替代专业医疗诊断。
          </div>
        </Card>
      </div>
    </main>
  );
}
