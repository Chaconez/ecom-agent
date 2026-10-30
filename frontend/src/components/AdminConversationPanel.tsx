import { useCallback, useEffect, useRef, useState } from 'react';
import {
  fetchAdminConversations,
  fetchAdminMessages,
  takeoverConversation,
  releaseConversation,
  sendHumanMessage,
  type AdminConversation,
  type AdminMessage,
} from '../api/adminChat';
import { useAuthStore } from '../store/authStore';

/** 会话列表 + 当前会话消息的轮询间隔（ms） */
const POLL_INTERVAL = 5000;

function formatTime(ts: number): string {
  if (!ts) return '-';
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

/**
 * 客服工作台 · 会话面板。
 *
 * <p>左栏全部会话（含 AI / 人工接管标识），右侧回放消息并可接管、发言、结束接管。
 * 人工发言通过 `/api/admin/conversations/{id}/human-message` 落库，C 端 HUMAN 模式下
 * 3s 轮询即可看到，因此「转人工」链路在统一入口内闭环。</p>
 */
export default function AdminConversationPanel() {
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const [conversations, setConversations] = useState<AdminConversation[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<AdminMessage[]>([]);
  const [inputText, setInputText] = useState('');
  const [listError, setListError] = useState('');
  const [actionError, setActionError] = useState('');
  const [busy, setBusy] = useState(false);

  // 结束接管：可选先发一条交接摘要
  const [handoverOpen, setHandoverOpen] = useState(false);
  const [handoverNote, setHandoverNote] = useState('');

  const bottomRef = useRef<HTMLDivElement>(null);
  // 用 ref 保存当前选中会话，避免轮询回调随选择变化而重建
  const selectedIdRef = useRef<string | null>(null);

  const selected = conversations.find((c) => c.conversationId === selectedId) ?? null;

  useEffect(() => {
    selectedIdRef.current = selectedId;
  }, [selectedId]);

  /** 统一错误处理：401 清登录态，403 提示无权限 */
  const handleError = useCallback(
    (err: any, setter: (msg: string) => void, fallback: string) => {
      if (err?.message === 'UNAUTHORIZED') {
        clearAuth();
        return;
      }
      if (err?.message === 'FORBIDDEN') {
        setter('无权访问：当前账号不是管理员');
        return;
      }
      setter(err?.message || fallback);
    },
    [clearAuth],
  );

  /** 刷新会话列表 + 当前选中会话的消息 */
  const refresh = useCallback(async () => {
    try {
      const convs = await fetchAdminConversations();
      setConversations(convs);
      setListError('');
      const id = selectedIdRef.current;
      if (id) {
        const msgs = await fetchAdminMessages(id);
        setMessages(msgs);
      }
    } catch (err: any) {
      handleError(err, setListError, '加载会话失败');
    }
  }, [handleError]);

  // 首次加载 + 轮询
  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, POLL_INTERVAL);
    return () => clearInterval(timer);
  }, [refresh]);

  // 新消息自动滚到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSelect = async (id: string) => {
    setSelectedId(id);
    setHandoverOpen(false);
    setHandoverNote('');
    setActionError('');
    try {
      const msgs = await fetchAdminMessages(id);
      setMessages(msgs);
    } catch (err: any) {
      handleError(err, setActionError, '加载消息失败');
    }
  };

  const handleTakeover = async () => {
    if (!selectedId) return;
    setBusy(true);
    setActionError('');
    try {
      await takeoverConversation(selectedId);
      await refresh();
      setMessages(await fetchAdminMessages(selectedId));
    } catch (err: any) {
      handleError(err, setActionError, '接管失败');
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
      if (note) await sendHumanMessage(selectedId, `【交接摘要】${note}`);
      await releaseConversation(selectedId);
      setHandoverOpen(false);
      setHandoverNote('');
      await refresh();
      setMessages(await fetchAdminMessages(selectedId));
    } catch (err: any) {
      handleError(err, setActionError, '结束接管失败');
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
      setMessages(await fetchAdminMessages(selectedId));
    } catch (err: any) {
      handleError(err, setActionError, '发送失败');
      setInputText(content);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="aw-body">
      {/* 左栏：全部会话 */}
      <aside className="aw-conv-panel">
        <div className="aw-panel-title">
          全部会话
          <span className="aw-panel-count">{conversations.length}</span>
        </div>
        {listError && <div className="aw-error aw-error-panel">{listError}</div>}
        <div className="aw-conv-list">
          {conversations.length === 0 && !listError && <div className="aw-empty">暂无会话</div>}
          {conversations.map((c) => (
            <div
              key={c.conversationId}
              className={`aw-conv-item${c.conversationId === selectedId ? ' active' : ''}${
                c.mode === 'HUMAN' ? ' human' : ''
              }`}
              onClick={() => handleSelect(c.conversationId)}
            >
              <div className="aw-conv-row">
                <span className="aw-conv-title">{c.title || '新对话'}</span>
                {c.mode === 'HUMAN' && <span className="aw-mode-badge">人工</span>}
              </div>
              <div className="aw-conv-preview">
                {c.previewRole === 'user' ? '用户: ' : ''}
                {c.preview || '（暂无消息）'}
              </div>
              <div className="aw-conv-meta">
                <span>{c.userId}</span>
                <span>{formatTime(c.updatedAt)}</span>
              </div>
            </div>
          ))}
        </div>
      </aside>

      {/* 右侧：会话详情 */}
      {selected ? (
        <section className="aw-chat">
          <div className="aw-chat-header">
            <div className="aw-chat-header-info">
              <span className="aw-chat-title">{selected.title || '新对话'}</span>
              <span className="aw-chat-user">用户: {selected.userId}</span>
              <span className={`aw-chat-mode${selected.mode === 'HUMAN' ? ' human' : ''}`}>
                {selected.mode === 'HUMAN' ? '● 人工接管中' : '○ AI 服务中'}
              </span>
            </div>
            {selected.mode === 'HUMAN' ? (
              <button
                className="aw-btn aw-btn-release"
                disabled={busy}
                onClick={() => {
                  setHandoverOpen((v) => !v);
                  setActionError('');
                }}
              >
                结束接管
              </button>
            ) : (
              <button className="aw-btn aw-btn-takeover" disabled={busy} onClick={handleTakeover}>
                接管会话
              </button>
            )}
          </div>

          {actionError && <div className="aw-error aw-error-main">{actionError}</div>}

          {handoverOpen && selected.mode === 'HUMAN' && (
            <div className="aw-handover">
              <div className="aw-handover-label">
                交接摘要（可选）：作为一条消息发给用户，说明后续安排
              </div>
              <textarea
                className="aw-handover-input"
                rows={2}
                placeholder="例如：您的问题已记录，售后专员将在 24 小时内联系您处理退款。"
                value={handoverNote}
                onChange={(e) => setHandoverNote(e.target.value)}
              />
              <div className="aw-handover-actions">
                <button
                  className="aw-btn aw-btn-ghost"
                  onClick={() => {
                    setHandoverOpen(false);
                    setHandoverNote('');
                  }}
                >
                  取消
                </button>
                <button className="aw-btn aw-btn-release" disabled={busy} onClick={handleRelease}>
                  确认结束接管
                </button>
              </div>
            </div>
          )}

          <div className="aw-msg-list">
            {messages.length === 0 && <div className="aw-empty">暂无消息</div>}
            {messages.map((m) => (
              <div key={m.id} className={`aw-msg-row ${m.role}`}>
                <div className="aw-msg-sender">{senderName(m)}</div>
                <div
                  className={`aw-msg-bubble${m.agentName?.startsWith('human:') ? ' human' : ''}`}
                >
                  {m.content}
                </div>
                <div className="aw-msg-time">{formatTime(m.timestamp)}</div>
              </div>
            ))}
            <div ref={bottomRef} />
          </div>

          <div className="aw-input-area">
            <textarea
              className="aw-input"
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
              className="aw-btn aw-btn-send"
              disabled={busy || !inputText.trim()}
              onClick={handleSend}
            >
              发送
            </button>
          </div>
          {selected.mode !== 'HUMAN' && (
            <div className="aw-input-hint">
              提示：AI 服务中的会话也可直接发言（消息立即送达用户），但建议先「接管会话」，避免 AI 同时回复。
            </div>
          )}
        </section>
      ) : (
        <section className="aw-chat aw-chat-empty">
          <div className="aw-empty">请从左侧选择一个会话</div>
        </section>
      )}
    </div>
  );
}
