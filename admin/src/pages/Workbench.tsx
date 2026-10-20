import { useCallback, useEffect, useRef, useState } from 'react';
import {
  fetchAdminConversations,
  fetchAdminMessages,
  takeoverConversation,
  releaseConversation,
  sendHumanMessage,
  type AdminConversation,
  type AdminMessage,
} from '../api/chat';
import {
  fetchApprovals,
  decideApproval,
  type AdminApproval,
  type ApprovalStatusFilter,
} from '../api/approval';
import { logout } from '../api/auth';
import { useAuthStore } from '../store/authStore';

/** 管理端轮询间隔（ms）：会话列表 + 当前会话消息 + 工单队列 */
const POLL_INTERVAL = 5000;

function formatTime(ts: number): string {
  const d = new Date(ts);
  const now = new Date();
  const isToday =
    d.getDate() === now.getDate() &&
    d.getMonth() === now.getMonth() &&
    d.getFullYear() === now.getFullYear();
  if (isToday) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

/** 人工客服消息展示名：agent_name = human:{adminUserId} */
function senderName(msg: AdminMessage): string {
  if (msg.agentName?.startsWith('human:')) return '人工客服';
  if (msg.role === 'user') return '用户';
  if (msg.role === 'system') return '系统';
  return msg.agentName || 'AI 助手';
}

export default function Workbench({
  onUnauthorized,
  userPortalUrl,
}: {
  onUnauthorized: () => void | Promise<void>;
  userPortalUrl: string;
}) {
  const { username, userId, clearAuth } = useAuthStore();

  const [tab, setTab] = useState<'chat' | 'approval'>('chat');
  const [conversations, setConversations] = useState<AdminConversation[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<AdminMessage[]>([]);
  const [inputText, setInputText] = useState('');
  const [listError, setListError] = useState('');
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(false);

  // 工单审批
  const [approvals, setApprovals] = useState<AdminApproval[]>([]);
  const [approvalFilter, setApprovalFilter] = useState<ApprovalStatusFilter>('PENDING');
  const [approvalError, setApprovalError] = useState('');

  // 结束接管：交接摘要面板
  const [handoverOpen, setHandoverOpen] = useState(false);
  const [handoverNote, setHandoverNote] = useState('');

  const bottomRef = useRef<HTMLDivElement>(null);

  const selected = conversations.find((c) => c.conversationId === selectedId) ?? null;

  const handleAuthError = useCallback(
    async (err: any) => {
      if (err?.message === 'UNAUTHORIZED') {
        await onUnauthorized();
        return true;
      }
      return false;
    },
    [onUnauthorized],
  );

  /** 刷新会话列表 + 当前选中会话的消息 */
  const refreshChat = useCallback(async () => {
    try {
      const convs = await fetchAdminConversations();
      setConversations(convs);
      setListError('');
      if (selectedId) {
        const msgs = await fetchAdminMessages(selectedId);
        setMessages(msgs);
      }
    } catch (err: any) {
      if (err?.message === 'FORBIDDEN') {
        setListError('无权访问：当前账号不是管理员');
        return;
      }
      if (!(await handleAuthError(err))) {
        setListError(err?.message || '加载失败');
      }
    }
  }, [selectedId, handleAuthError]);

  /** 刷新工单队列 */
  const refreshApprovals = useCallback(async () => {
    try {
      const list = await fetchApprovals(approvalFilter);
      setApprovals(list);
      setApprovalError('');
    } catch (err: any) {
      if (err?.message === 'FORBIDDEN') {
        setApprovalError('无权访问：当前账号不是管理员');
        return;
      }
      if (!(await handleAuthError(err))) {
        setApprovalError(err?.message || '加载失败');
      }
    }
  }, [approvalFilter, handleAuthError]);

  // 初始加载
  useEffect(() => {
    refreshChat();
    refreshApprovals();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 轮询：列表 + 当前会话消息 + 工单队列
  useEffect(() => {
    const timer = setInterval(() => {
      refreshChat();
      if (tab === 'approval') refreshApprovals();
    }, POLL_INTERVAL);
    return () => clearInterval(timer);
  }, [refreshChat, refreshApprovals, tab]);

  // 切换筛选时立即刷新工单
  useEffect(() => {
    refreshApprovals();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [approvalFilter]);

  // 自动滚动到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSelect = async (id: string) => {
    setSelectedId(id);
    setHandoverOpen(false);
    setActionError('');
    try {
      const msgs = await fetchAdminMessages(id);
      setMessages(msgs);
    } catch (err: any) {
      if (!(await handleAuthError(err))) {
        setActionError(err?.message || '加载消息失败');
      }
    }
  };

  const handleTakeover = async () => {
    if (!selectedId) return;
    setBusy(true);
    setActionError('');
    try {
      await takeoverConversation(selectedId);
      await refreshChat();
      const msgs = await fetchAdminMessages(selectedId);
      setMessages(msgs);
    } catch (err: any) {
      if (!(await handleAuthError(err))) setActionError(err?.message || '接管失败');
    } finally {
      setBusy(false);
    }
  };

  /** 结束接管：可选先发一条交接摘要（人工消息），再切回 AI 模式 */
  const handleRelease = async () => {
    if (!selectedId) return;
    setBusy(true);
    setActionError('');
    try {
      const note = handoverNote.trim();
      if (note) {
        await sendHumanMessage(selectedId, `【交接摘要】${note}`);
      }
      await releaseConversation(selectedId);
      setHandoverOpen(false);
      setHandoverNote('');
      await refreshChat();
      const msgs = await fetchAdminMessages(selectedId);
      setMessages(msgs);
    } catch (err: any) {
      if (!(await handleAuthError(err))) setActionError(err?.message || '结束接管失败');
    } finally {
      setBusy(false);
    }
  };

  const handleSend = async () => {
    if (!selectedId || !inputText.trim()) return;
    const content = inputText.trim();
    setInputText('');
    setBusy(true);
    setActionError('');
    try {
      await sendHumanMessage(selectedId, content);
      const msgs = await fetchAdminMessages(selectedId);
      setMessages(msgs);
    } catch (err: any) {
      if (!(await handleAuthError(err))) setActionError(err?.message || '发送失败');
      setInputText(content);
    } finally {
      setBusy(false);
    }
  };

  const handleDecide = async (approval: AdminApproval, decision: 'agree' | 'refuse') => {
    setBusy(true);
    setApprovalError('');
    try {
      await decideApproval(approval.processInstanceId, decision);
      await refreshApprovals();
    } catch (err: any) {
      if (!(await handleAuthError(err))) {
        setApprovalError(err?.message || '审批提交失败');
      }
    } finally {
      setBusy(false);
    }
  };

  const handleLogout = async () => {
    await logout(useAuthStore.getState().token ?? '');
    clearAuth();
  };

  const initial = username ? username.charAt(0).toUpperCase() : 'A';

  return (
    <div className="wb-root">
      {/* 顶栏 */}
      <header className="wb-header">
        <div className="wb-brand">
          <span className="wb-brand-icon">🛡️</span>
          <div>
            <div className="wb-brand-name">
              电商智能客服 · 管理端
              <span className="portal-badge">管理端</span>
            </div>
            <div className="wb-brand-sub">客服工作台</div>
          </div>
        </div>
        <div className="wb-header-right">
          <a className="wb-user-portal-link" href={userPortalUrl} target="_blank" rel="noreferrer">
            用户端 ↗
          </a>
          <div className="wb-user">
            <div className="wb-user-avatar">{initial}</div>
            <div className="wb-user-info">
              <div className="wb-user-name">{username}</div>
              <div className="wb-user-id">{userId}</div>
            </div>
          </div>
          <button className="wb-logout" onClick={handleLogout}>
            退出
          </button>
        </div>
      </header>

      <div className="wb-body">
        {/* 左侧：会话列表 */}
        <aside className="wb-conv-panel">
          <div className="wb-panel-title">
            全部会话
            <span className="wb-panel-count">{conversations.length}</span>
          </div>
          {listError && <div className="wb-error">{listError}</div>}
          <div className="wb-conv-list">
            {conversations.length === 0 && !listError && (
              <div className="wb-empty">暂无会话</div>
            )}
            {conversations.map((c) => (
              <div
                key={c.conversationId}
                className={`wb-conv-item${c.conversationId === selectedId ? ' active' : ''}${
                  c.mode === 'HUMAN' ? ' human' : ''
                }`}
                onClick={() => handleSelect(c.conversationId)}
              >
                <div className="wb-conv-row">
                  <span className="wb-conv-title">{c.title || '新对话'}</span>
                  {c.mode === 'HUMAN' && <span className="wb-mode-badge">人工接管</span>}
                </div>
                <div className="wb-conv-preview">
                  {c.previewRole === 'user' ? '用户: ' : ''}
                  {c.preview || '（暂无消息）'}
                </div>
                <div className="wb-conv-meta">
                  <span>{c.userId}</span>
                  <span>{formatTime(c.updatedAt)}</span>
                </div>
              </div>
            ))}
          </div>
        </aside>

        {/* 右侧：主区域 */}
        <main className="wb-main">
          <div className="wb-tabs">
            <button
              className={`wb-tab${tab === 'chat' ? ' active' : ''}`}
              onClick={() => setTab('chat')}
            >
              💬 会话工作台
            </button>
            <button
              className={`wb-tab${tab === 'approval' ? ' active' : ''}`}
              onClick={() => setTab('approval')}
            >
              📋 工单审批
              {approvals.some((a) => a.status === 'PENDING') && (
                <span className="wb-tab-dot" />
              )}
            </button>
          </div>

          {actionError && <div className="wb-error wb-error-main">{actionError}</div>}

          {tab === 'chat' ? (
            selected ? (
              <div className="wb-chat">
                {/* 会话头：信息 + 接管控制 */}
                <div className="wb-chat-header">
                  <div className="wb-chat-header-info">
                    <span className="wb-chat-title">{selected.title || '新对话'}</span>
                    <span className="wb-chat-user">用户: {selected.userId}</span>
                    <span className={`wb-chat-mode${selected.mode === 'HUMAN' ? ' human' : ''}`}>
                      {selected.mode === 'HUMAN' ? '● 人工接管中' : '○ AI 服务中'}
                    </span>
                  </div>
                  <div className="wb-chat-header-actions">
                    {selected.mode === 'HUMAN' ? (
                      <button
                        className="wb-btn release"
                        disabled={busy}
                        onClick={() => {
                          setHandoverOpen((v) => !v);
                          setActionError('');
                        }}
                      >
                        结束接管
                      </button>
                    ) : (
                      <button className="wb-btn takeover" disabled={busy} onClick={handleTakeover}>
                        接管会话
                      </button>
                    )}
                  </div>
                </div>

                {/* 结束接管：交接摘要 */}
                {handoverOpen && selected.mode === 'HUMAN' && (
                  <div className="wb-handover">
                    <div className="wb-handover-label">
                      交接摘要（可选）：将作为一条消息发给用户，便于其了解后续安排
                    </div>
                    <textarea
                      className="wb-handover-input"
                      rows={2}
                      placeholder="例如：您的问题已记录，售后专员将在 24 小时内联系您处理退款。"
                      value={handoverNote}
                      onChange={(e) => setHandoverNote(e.target.value)}
                    />
                    <div className="wb-handover-actions">
                      <button
                        className="wb-btn ghost"
                        onClick={() => {
                          setHandoverOpen(false);
                          setHandoverNote('');
                        }}
                      >
                        取消
                      </button>
                      <button className="wb-btn release" disabled={busy} onClick={handleRelease}>
                        确认结束接管
                      </button>
                    </div>
                  </div>
                )}

                {/* 消息回放 */}
                <div className="wb-msg-list">
                  {messages.length === 0 && <div className="wb-empty">暂无消息</div>}
                  {messages.map((m) => (
                    <div key={m.id} className={`wb-msg-row ${m.role}`}>
                      <div className="wb-msg-sender">{senderName(m)}</div>
                      <div
                        className={`wb-msg-bubble${m.agentName?.startsWith('human:') ? ' human' : ''}`}
                      >
                        {m.content}
                      </div>
                      <div className="wb-msg-time">{formatTime(m.timestamp)}</div>
                    </div>
                  ))}
                  <div ref={bottomRef} />
                </div>

                {/* 人工发言输入 */}
                <div className="wb-input-area">
                  <textarea
                    className="wb-input"
                    rows={2}
                    placeholder={
                      selected.mode === 'HUMAN'
                        ? '以人工客服身份回复用户（Enter 发送，Shift+Enter 换行）'
                        : '当前为 AI 服务中。如需人工介入，请先点击右上角「接管会话」'
                    }
                    value={inputText}
                    onChange={(e) => setInputText(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        handleSend();
                      }
                    }}
                    disabled={busy}
                  />
                  <button
                    className="wb-btn send"
                    disabled={busy || !inputText.trim()}
                    onClick={handleSend}
                  >
                    发送
                  </button>
                </div>
                {selected.mode !== 'HUMAN' && (
                  <div className="wb-input-hint">
                    提示：AI 服务中的会话也可以直接发言（消息会立即送达用户），但建议先「接管会话」避免 AI 继续回复。
                  </div>
                )}
              </div>
            ) : (
              <div className="wb-empty-main">请从左侧选择一个会话</div>
            )
          ) : (
            /* 工单审批 */
            <div className="wb-approval">
              <div className="wb-approval-toolbar">
                <span className="wb-panel-title">工单队列</span>
                <select
                  className="wb-filter-select"
                  value={approvalFilter}
                  onChange={(e) => setApprovalFilter(e.target.value as ApprovalStatusFilter)}
                >
                  <option value="PENDING">待处理</option>
                  <option value="APPROVED">已通过</option>
                  <option value="REJECTED">已驳回</option>
                  <option value="CANCELLED">已取消</option>
                  <option value="">全部</option>
                </select>
              </div>
              {approvalError && <div className="wb-error wb-error-main">{approvalError}</div>}
              <div className="wb-approval-list">
                {approvals.length === 0 && !approvalError && (
                  <div className="wb-empty">暂无工单</div>
                )}
                {approvals.map((a) => (
                  <div key={a.processInstanceId} className="wb-approval-card">
                    <div className="wb-approval-head">
                      <span className="wb-approval-title">{a.title}</span>
                      <span className={`wb-approval-status ${a.status.toLowerCase()}`}>
                        {a.statusLabel}
                      </span>
                    </div>
                    <div className="wb-approval-meta">
                      <span>申请人: {a.userName || a.userId}</span>
                      {a.orderId && <span>关联订单: {a.orderId}</span>}
                      {a.submitTime && <span>提交: {formatTime(a.submitTime)}</span>}
                    </div>
                    {a.form && typeof a.form === 'object' && (
                      <div className="wb-approval-form">
                        {Object.entries(a.form).map(([k, v]) => (
                          <span key={k} className="wb-form-item">
                            {k}: {String(v)}
                          </span>
                        ))}
                      </div>
                    )}
                    {a.remark && <div className="wb-approval-remark">批复: {a.remark}</div>}
                    {a.status === 'PENDING' && (
                      <div className="wb-approval-actions">
                        <button
                          className="wb-btn approve"
                          disabled={busy}
                          onClick={() => handleDecide(a, 'agree')}
                        >
                          通过
                        </button>
                        <button
                          className="wb-btn reject"
                          disabled={busy}
                          onClick={() => handleDecide(a, 'refuse')}
                        >
                          驳回
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
