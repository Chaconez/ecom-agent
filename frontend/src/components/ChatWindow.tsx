import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { flushSync } from 'react-dom';
import { useChatStore, isPersistedMessageId } from '../store/chatStore';
import { useAuthStore } from '../store/authStore';
import { useUiStore } from '../store/uiStore';
import { logout } from '../api/auth';
import { sendChatMessageSSE, sendUserResponseSSE, interruptAgent, fetchMessages, fetchConversations, updateMessageFeedback, fetchDebugAgents, sendDebugAgentMessageSSE, type DebugAgentInfo, type RemoteMessage } from '../api/chat';
import AgentMessageBlock from './AgentMessageBlock';
import UserInteractionCard from './UserInteractionCard';
import type { Message, PlanTask, UserInteraction, AgentResultData, OrderResult, PlanHtmlData } from '../types';

/** HUMAN 模式（人工客服接管）下的轮询间隔（ms） */
const HUMAN_MODE_POLL_INTERVAL = 3000;

/** 把后端持久化消息映射为前端 Message（挂载加载与 HUMAN 轮询共用） */
function toLocalMessage(m: RemoteMessage): Message {
  return {
    id: m.id,
    role: m.role,
    content: m.content,
    agentName: m.agentName,
    timestamp: m.timestamp,
    progress: m.extra?.progress,
    thinkingByAgent: m.extra?.thinkingByAgent,
    planTasks: m.extra?.planTasks,
    resultData: m.extra?.resultData,
    timeline: m.extra?.timeline,
    feedback: m.feedback ?? null,
    feedbackAt: m.feedbackAt,
  };
}

// ── Message bubble components ─────────────────────────────────────────────
function UserBubble({ msg, username }: { msg: Message; username: string }) {
  // 取用户名首字符作为头像
  const initial = username ? username.charAt(0).toUpperCase() : '我';
  return (
    <div className="msg-row user">
      <div className="msg-user-bubble">{msg.content}</div>
      <div className="msg-avatar user-avatar">{initial}</div>
    </div>
  );
}

function SuggestionChips({
  questions,
  onClick,
}: {
  questions: string[];
  onClick: (q: string) => void;
}) {
  if (!questions.length) return null;
  return (
    <div className="msg-row agent">
      <div className="msg-avatar agent-avatar" style={{ visibility: 'hidden' }} />
      <div className="msg-agent-content">
        <div className="suggestion-chips">
          {questions.map((q) => (
            <button
              key={q}
              className="suggestion-chip"
              onClick={() => onClick(q)}
              title={q}
              type="button"
            >
              {q}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function SystemBubble({ msg }: { msg: Message }) {
  return (
    <div className="msg-row system">
      <div className="msg-system-bubble">
        <svg viewBox="0 0 16 16" fill="none" width="13" height="13" style={{ flexShrink: 0 }}>
          <circle cx="8" cy="8" r="6.5" stroke="#d46b08" strokeWidth="1.2" />
          <path d="M8 5v3.5M8 10.5v.5" stroke="#d46b08" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
        {msg.content}
      </div>
    </div>
  );
}

// ── Thinking indicator ────────────────────────────────────────────────────
function ThinkingDots() {
  return (
    <div className="msg-row agent">
      <div className="msg-avatar agent-avatar">
        <svg viewBox="0 0 20 20" fill="none" width="18" height="18">
          <path
            d="M10 2C5.58 2 2 5.36 2 9.5c0 2.1.9 4 2.34 5.35L3.5 18l3.5-1.75c.93.31 1.93.5 2.99.5 4.42 0 8-3.36 8-7.5S14.42 2 10 2z"
            fill="white"
            fillOpacity="0.9"
          />
        </svg>
      </div>
      <div className="msg-agent-content">
        <div className="msg-agent-name">电商智能客服</div>
        <div className="thinking-indicator">
          <span className="dot" />
          <span className="dot" />
          <span className="dot" />
        </div>
      </div>
    </div>
  );
}

// ── Main ChatWindow component ─────────────────────────────────────────────

/**
 * 判断条目是否为 MCP 内容块（形如 {type:'text', text:'...'}）。
 * 这类条目是工具的原始输出包装，而非可展示的业务数据。
 */
function isRawContentBlock(item: unknown): boolean {
  if (!item || typeof item !== 'object') return false;
  const obj = item as Record<string, unknown>;
  return obj.type === 'text' && typeof obj.text === 'string';
}

/**
 * 清洗后端推送的 result_data：
 * - 过滤掉 MCP 内容块类条目，避免将原始 JSON 直接展示为卡片；
 * - 若无有效条目则返回 null（不渲染卡片，由正文 Markdown 展示）。
 */
function sanitizeResultData(td: AgentResultData | null | undefined): AgentResultData | null {
  if (!td || !td.type || !Array.isArray(td.items)) return null;
  const items = (td.items as unknown[]).filter((it) => !isRawContentBlock(it));
  if (items.length === 0) return null;
  return { ...td, items: items as AgentResultData['items'] };
}

export default function ChatWindow() {
  const [inputText, setInputText] = useState('');
  const [interaction, setInteraction] = useState<UserInteraction | null>(null);
  // 【调试后门】直连的目标子智能体，空字符串表示走默认智能调度（MasterAgent）
  const [debugAgent, setDebugAgent] = useState('');
  const [debugAgents, setDebugAgents] = useState<DebugAgentInfo[]>([]);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const username = useAuthStore((s) => s.username) ?? '我';
  const { token, clearAuth } = useAuthStore();

  // 跨页面预填入口（“我的订单”快捷动作）：非空则自动发送一次
  const pendingUserMessage = useUiStore((s) => s.pendingUserMessage);
  const consumePendingUserMessage = useUiStore((s) => s.consumePendingUserMessage);

  /** Token 失效时统一登出并跳回登录页 */
  const handleUnauthorized = async () => {
    await logout(token ?? '');
    clearAuth();
  };

  const {
    conversations,
    currentConversationId,
    addMessage,
    appendToLastAgentMessage,
    addProgressStep,
    updatePlanTasks,
    addResultData,
    snapshotProgressToLastMessage,
    clearProgress,
    appendThinking,
    startThinkingRound,
    setThinking,
    setActiveAgent,
    setSuggestedQuestions,
    clearSuggestedQuestions,
    loadConversationMessages,
    addTimelineItem,
    updateTimelineTool,
    appendTimelineThinking,
    setTimelinePlan,
    setMessageFeedback,
    setLastAgentMessageId,
    setConversationMode,
  } = useChatStore();

  const currentConv = conversations.find((c) => c.id === currentConversationId);
  const messages = currentConv?.messages ?? [];
  const isThinking = currentConv?.isThinking ?? false;
  const timeline = currentConv?.timeline ?? [];
  const suggestedQuestions = currentConv?.suggestedQuestions ?? [];
  /** 人工客服接管模式：转人工后由管理端客服应答，AI 不再响应 */
  const isHumanMode = currentConv?.mode === 'HUMAN';

  const lastMsg = messages.length > 0 ? messages[messages.length - 1] : null;
  const lastAgentIsStreaming =
    isThinking && lastMsg?.role === 'agent' && !lastMsg.timeline;

  // 从后端加载远程会话的历史消息
  useEffect(() => {
    if (!currentConv || !currentConv.isRemote || currentConv.isLoaded) return;
    let cancelled = false;
    fetchMessages(currentConversationId)
      .then((remoteMessages) => {
        if (cancelled) return;
        loadConversationMessages(currentConversationId, remoteMessages.map(toLocalMessage));
      })
      .catch((err) => {
        if (err?.message === 'UNAUTHORIZED') {
          handleUnauthorized();
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentConversationId]);

  // Auto-scroll to bottom
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isThinking, timeline]);

  /**
   * HUMAN 模式（人工客服接管）轮询：
   * - 每 3s 拉取会话列表刷新 mode（管理端「结束接管」后及时恢复 AI）；
   * - 同时拉取消息列表，把管理端客服的发言合并进本地（服务端权威 + 本地未持久化消息去重保留）；
   * - 轮询期间不打断 SSE：发送瞬间的转人工提示仍走流式事件，随后被服务端系统消息替换。
   */
  useEffect(() => {
    if (!isHumanMode || !currentConversationId) return;
    let cancelled = false;
    const poll = async () => {
      if (cancelled) return;
      try {
        const [convs, remoteMessages] = await Promise.all([
          fetchConversations(),
          fetchMessages(currentConversationId),
        ]);
        if (cancelled) return;
        const remoteConv = convs.find((c) => c.id === currentConversationId);
        if (remoteConv) {
          setConversationMode(currentConversationId, remoteConv.mode === 'HUMAN' ? 'HUMAN' : 'AI');
        }
        // 服务端权威列表
        const mapped = remoteMessages.map(toLocalMessage);
        // 本地未持久化消息（如新会话欢迎语）去重后保留；刚发送的用户消息服务端已落库，按内容去重
        const state = useChatStore.getState();
        const conv = state.conversations.find((c) => c.id === currentConversationId);
        const localOnly = (conv?.messages ?? []).filter(
          (m) =>
            !isPersistedMessageId(m.id) &&
            !mapped.some((r) => r.role === m.role && r.content === m.content),
        );
        loadConversationMessages(
          currentConversationId,
          [...localOnly, ...mapped].sort((a, b) => a.timestamp - b.timestamp),
        );
      } catch (err: any) {
        if (err?.message === 'UNAUTHORIZED') {
          handleUnauthorized();
        }
        // 其余网络错误静默重试（下一轮轮询兜底）
      }
    };
    const timer = setInterval(poll, HUMAN_MODE_POLL_INTERVAL);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isHumanMode, currentConversationId]);

  // 加载可直连的子智能体列表（供右上角切换使用）
  useEffect(() => {
    fetchDebugAgents()
      .then(setDebugAgents)
      .catch(() => {
        // 调试后门不可用时静默忽略，正常对话不受影响
      });
  }, []);

  // 流式输出结束时，把当前时间轴快照到最后一条 agent 消息，避免正文出现后进度丢失
  const prevIsThinkingRef = useRef(isThinking);
  useEffect(() => {
    if (prevIsThinkingRef.current && !isThinking) {
      snapshotProgressToLastMessage();
    }
    prevIsThinkingRef.current = isThinking;
  }, [isThinking, snapshotProgressToLastMessage]);

  // Auto-resize textarea
  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 140) + 'px';
  }, [inputText]);

  const handleEvent = (event: string, data: string) => {
    switch (event) {
      case 'agent-switch':
        setActiveAgent(data);
        break;
      case 'message':
        appendToLastAgentMessage(data);
        break;
      case 'message_id':
        // 后端回复落库后下发的真实 messageId，回填后点赞/点踩才能命中数据库记录
        setLastAgentMessageId(data.trim());
        break;
      case 'thinking': {
        try {
          const d = JSON.parse(data) as { agentName: string; text?: string; roundStart?: string };
          if (!d.agentName) break;
          if (d.roundStart === 'true') {
            // 新一轮思考开始
            startThinkingRound(d.agentName);
            appendTimelineThinking(d.agentName, '', true);
          } else if (d.text) {
            appendThinking(d.agentName, d.text);
            appendTimelineThinking(d.agentName, d.text);
          }
        } catch {
          // ignore malformed
        }
        break;
      }
      case 'plan_update': {
        try {
          const d = JSON.parse(data) as {
            type: string;
            agentName: string;
            planName: string;
            tasks: PlanTask[];
          };
          if (Array.isArray(d.tasks)) {
            try {
              flushSync(() => {
                updatePlanTasks(d.tasks);
                setTimelinePlan(d.tasks);
              });
            } catch {
              updatePlanTasks(d.tasks);
              setTimelinePlan(d.tasks);
            }
          }
        } catch {
          // ignore malformed
        }
        break;
      }
      case 'progress': {
        let progressData: {
          type: string;
          stepId: string;
          agentName: string;
          toolName?: string;
          message: string;
          result?: string;
          arguments?: string;
        };
        try {
          progressData = JSON.parse(data);
        } catch {
          break;
        }
        if (!progressData.stepId) break;
        const isDone = progressData.type === 'tool_done' || progressData.type === 'agent_done';
        const step = {
          id: progressData.stepId,
          title: progressData.message,
          status: (isDone ? 'done' : 'in-progress') as 'done' | 'in-progress',
          agentName: progressData.agentName,
          result: progressData.result,
          arguments: progressData.arguments,
        };
        try {
          flushSync(() => addProgressStep(step));
        } catch {
          addProgressStep(step);
        }
        if (progressData.type === 'tool_call') {
          addTimelineItem({
            kind: 'tool',
            id: progressData.stepId,
            agentName: progressData.agentName,
            title: progressData.message,
            status: 'in-progress',
            arguments: progressData.arguments,
          });
        } else if (progressData.type === 'tool_done') {
          updateTimelineTool(progressData.stepId, {
            status: 'done',
            title: progressData.message,
            result: progressData.result,
          });
        }
        break;
      }
      case 'error':
        addMessage({
          id: Math.random().toString(36).slice(2, 10),
          role: 'system',
          content: `错误：${data}`,
          timestamp: Date.now(),
        });
        break;
      case 'user_interaction': {
        try {
          const d = JSON.parse(data) as UserInteraction;
          // 触发用户交互时立即把当前轮次的进度/时间轴固化为一条消息，
          // 避免因随后 setThinking(false) 导致“流式占位”渲染条件失效，
          // 让前面的进度信息在交互卡片出现后从界面上消失。
          snapshotProgressToLastMessage();
          setInteraction(d);
          setThinking(false);
        } catch {
          // ignore malformed
        }
        break;
      }
      case 'interrupted':
        setThinking(false);
        clearSuggestedQuestions();
        addMessage({
          id: Math.random().toString(36).slice(2, 10),
          role: 'system',
          content: '已停止生成',
          timestamp: Date.now(),
        });
        break;
      case 'suggestions': {
        try {
          const questions = JSON.parse(data) as string[];
          if (Array.isArray(questions)) {
            setSuggestedQuestions(questions.filter((q) => typeof q === 'string' && q.length > 0));
          }
        } catch {
          // ignore malformed
        }
        break;
      }
      case 'result_data': {
        try {
          const td = JSON.parse(data) as AgentResultData;
          const sanitized = sanitizeResultData(td);
          if (sanitized) {
            addResultData(sanitized);
            addTimelineItem({ kind: 'result', data: sanitized });
          }
        } catch {
          // ignore malformed
        }
        break;
      }
      case 'order_result': {
        try {
          const br = JSON.parse(data) as OrderResult;
          if (br && br.orderId) {
            addTimelineItem({ kind: 'order', data: br });
          }
        } catch {
          // ignore malformed
        }
        break;
      }
      case 'plan_html': {
        try {
          const d = JSON.parse(data) as PlanHtmlData;
          if (d && d.html) {
            addTimelineItem({ kind: 'plan_html', data: d });
          }
        } catch {
          // ignore malformed
        }
        break;
      }
      default:
        break;
    }
  };

  const handleRespond = async (response: unknown) => {
    if (!interaction) return;
    const displayText = typeof response === 'string' ? response : JSON.stringify(response);
    addMessage({
      id: Math.random().toString(36).slice(2, 10),
      role: 'user',
      content: displayText,
      timestamp: Date.now(),
    });
    const currentInteraction = interaction;
    setInteraction(null);
    setThinking(true);

    try {
      await sendUserResponseSSE(
        currentConversationId,
        currentInteraction.toolUseId,
        response,
        (evt) => handleEvent(evt.event, evt.data),
        (err) => {
          if (err?.message === 'UNAUTHORIZED') {
            setThinking(false);
            handleUnauthorized();
            return;
          }
          addMessage({
            id: Math.random().toString(36).slice(2, 10),
            role: 'system',
            content: `连接异常：${err?.message || '未知错误'}`,
            timestamp: Date.now(),
          });
        },
        () => setThinking(false),
      );
    } catch (err: any) {
      setThinking(false);
      if (err?.message === 'UNAUTHORIZED') {
        await handleUnauthorized();
        return;
      }
      addMessage({
        id: Math.random().toString(36).slice(2, 10),
        role: 'system',
        content: `发送失败：${err?.message || '请检查后端服务是否启动'}`,
        timestamp: Date.now(),
      });
    }
  };

  const handleInterrupt = async () => {
    if (!isThinking) return;
    try {
      await interruptAgent(currentConversationId);
    } catch (err: any) {
      if (err?.message === 'UNAUTHORIZED') {
        await handleUnauthorized();
        return;
      }
      console.error('打断失败', err);
    }
  };

  /**
   * 处理用户对 AI 回复的点赞/点踩反馈。
   * 先乐观更新本地状态，调用失败时回滚到上一次状态。
   */
  const handleFeedback = async (
    messageId: string,
    next: 'LIKE' | 'DISLIKE' | null,
  ) => {
    // 本地临时 id（未收到后端 message_id）无法反馈，直接忽略避免请求 404
    if (!isPersistedMessageId(messageId)) {
      return;
    }
    const target = messages.find((m) => m.id === messageId);
    const prev = target?.feedback ?? null;
    // 再次点击同一选项视为取消
    const finalNext: 'LIKE' | 'DISLIKE' | null = prev === next ? null : next;
    setMessageFeedback(currentConversationId, messageId, finalNext);
    try {
      await updateMessageFeedback(currentConversationId, messageId, finalNext);
    } catch (err: any) {
      // 失败时回滚
      setMessageFeedback(currentConversationId, messageId, prev);
      if (err?.message === 'UNAUTHORIZED') {
        await handleUnauthorized();
        return;
      }
      addMessage({
        id: Math.random().toString(36).slice(2, 10),
        role: 'system',
        content: `反馈提交失败：${err?.message || '未知错误'}`,
        timestamp: Date.now(),
      });
    }
  };

  const sendText = async (content: string) => {
    if (interaction) return;
    if (!content.trim()) return;

    // 若正在生成，先打断上一轮回复
    if (isThinking) {
      try {
        await interruptAgent(currentConversationId);
      } catch (err: any) {
        if (err?.message === 'UNAUTHORIZED') {
          await handleUnauthorized();
          return;
        }
        console.error('打断失败', err);
      }
    }

    // 先把上一轮进度快照到上一条 agent 消息、清空 store，再追加本轮 user 消息。
    // 顺序不能反：若在追加 user 消息后再 snapshot，此时 messages 末尾是 user，
    // snapshotProgressToLastMessage 会走“无 agent 兜底”分支新建一条空白 agent 消息，
    // 导致上一轮的进度/时间轴以 phantom 消息形式出现在用户对话下方。
    snapshotProgressToLastMessage();
    clearProgress();
    clearSuggestedQuestions();

    addMessage({
      id: Math.random().toString(36).slice(2, 10),
      role: 'user',
      content,
      timestamp: Date.now(),
    });
    // HUMAN 模式（人工客服接管）不进入 AI 思考态：消息由人工回复，经轮询展示
    if (!isHumanMode) {
      setThinking(true);
    }
    // 直连调试模式下，把展示用的 activeAgent 切到目标智能体
    if (debugAgent) {
      setActiveAgent(debugAgent);
    }

    const onEvt = (evt: { event: string; data: string }) => handleEvent(evt.event, evt.data);
    const onErr = (err: any) => {
      if (err?.message === 'UNAUTHORIZED') {
        setThinking(false);
        handleUnauthorized();
        return;
      }
      addMessage({
        id: Math.random().toString(36).slice(2, 10),
        role: 'system',
        content: `连接异常：${err?.message || '未知错误'}`,
        timestamp: Date.now(),
      });
    };
    const onDone = () => {
      setThinking(false);
      // 发送后同步会话模式：命中「转人工」时后端已切到 HUMAN，本地及时跟进并启动轮询
      fetchConversations()
        .then((convs) => {
          const c = convs.find((x) => x.id === currentConversationId);
          if (c) {
            setConversationMode(currentConversationId, c.mode === 'HUMAN' ? 'HUMAN' : 'AI');
          }
        })
        .catch(() => {
          // 模式同步失败不阻塞：HUMAN 轮询启动失败时用户仍可手动刷新
        });
    };

    try {
      if (debugAgent) {
        await sendDebugAgentMessageSSE(debugAgent, currentConversationId, content, onEvt, onErr, onDone);
      } else {
        await sendChatMessageSSE(currentConversationId, content, onEvt, onErr, onDone);
      }
    } catch (err: any) {
      setThinking(false);
      if (err?.message === 'UNAUTHORIZED') {
        // Token 已失效，自动登出（clearAuth 会触发 App 渲染登录页）
        await handleUnauthorized();
        return;
      }
      addMessage({
        id: Math.random().toString(36).slice(2, 10),
        role: 'system',
        content: `发送失败：${err?.message || '请检查后端服务是否启动'}`,
        timestamp: Date.now(),
      });
    }
  };

  const handleSend = async () => {
    if (!inputText.trim()) return;
    const content = inputText.trim();
    setInputText('');
    await sendText(content);
  };

  // 保存最新 sendText 引用，供 pendingUserMessage useEffect 中使用时避免因依赖变化重复触发
  const sendTextRef = useRef(sendText);
  useEffect(() => {
    sendTextRef.current = sendText;
  });

  // 监听“我的订单”页面写入的 pendingUserMessage，自动发送一次。
  // 依赖 currentConversationId 确保新会话创建后再发送；仅在 message + 会话同时就绪时触发。
  useEffect(() => {
    if (!pendingUserMessage || !currentConversationId) return;
    // 正在处理中或等待用户回答时不自动发送，避免打断
    if (isThinking || interaction) return;
    const msg = consumePendingUserMessage();
    if (msg) {
      sendTextRef.current(msg);
    }
  }, [pendingUserMessage, currentConversationId, isThinking, interaction, consumePendingUserMessage]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="chat-window">
      {/* Header */}
      <div className="chat-header">
        <div className="chat-header-left">
          <svg viewBox="0 0 20 20" fill="none" width="20" height="20" className="chat-header-icon">
            <path
              d="M10 2C5.58 2 2 5.36 2 9.5c0 2.1.9 4 2.34 5.35L3.5 18l3.5-1.75c.93.31 1.93.5 2.99.5 4.42 0 8-3.36 8-7.5S14.42 2 10 2z"
              fill="#1677ff"
            />
          </svg>
          <span className="chat-header-title">{currentConv?.title || '新对话'}</span>
        </div>
        <div className="chat-header-right">
          {isHumanMode ? (
            <div className="chat-header-status human-mode">
              <span className="status-dot human-dot" />
              人工客服接管中
            </div>
          ) : (
            isThinking && (
              <div className="chat-header-status">
                <span className="status-dot" />
                AI 处理中...
              </div>
            )
          )}
          <select
            className="debug-agent-select"
            value={debugAgent}
            onChange={(e) => setDebugAgent(e.target.value)}
            disabled={isThinking || !!interaction}
            title="选择直连的子智能体（调试），默认由主智能体智能调度"
          >
            <option value="">🧠 默认（智能调度）</option>
            {debugAgents.map((a) => (
              <option key={a.name} value={a.name}>
                🔌 {a.label} （仅用于调试）
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Message list */}
      <div className="message-list">
        {messages.map((msg, idx) => {
          if (msg.role === 'user') return <UserBubble key={msg.id} msg={msg} username={username} />;
          if (msg.role === 'agent') {
            const isLast = idx === messages.length - 1;
            const streaming = isLast && lastAgentIsStreaming;
            return (
              <AgentMessageBlock
                key={msg.id}
                msg={msg}
                liveTimeline={streaming ? timeline : undefined}
                isStreaming={streaming}
                onFeedback={handleFeedback}
              />
            );
          }
          return <SystemBubble key={msg.id} msg={msg} />;
        })}

        {/* 处理中但还没有 agent 消息时，先用占位消息展示时间轴 */}
        {isThinking && !lastAgentIsStreaming && timeline.length > 0 && (
          <AgentMessageBlock
            key="streaming-placeholder"
            msg={{
              id: 'streaming-placeholder',
              role: 'agent',
              content: '',
              agentName: currentConv?.activeAgent,
              timestamp: Date.now(),
            }}
            liveTimeline={timeline}
            isStreaming
          />
        )}

        {/* 兜底思考动画：处理中且没有任何时间轴内容 */}
        {isThinking && !lastAgentIsStreaming && timeline.length === 0 && <ThinkingDots />}

        {/* HUMAN 模式等待提示：消息已发给人工客服，等待回复 */}
        {isHumanMode && !isThinking && lastMsg?.role === 'user' && (
          <div className="msg-row system">
            <div className="msg-system-bubble human-waiting">
              <span className="human-waiting-dots">
                <span className="dot" />
                <span className="dot" />
                <span className="dot" />
              </span>
              消息已发送，人工客服将尽快回复，请稍候...
            </div>
          </div>
        )}

        {/* 推荐问题：MasterAgent 返回后由问题推荐智能体生成 */}
        {!isThinking && suggestedQuestions.length > 0 && (
          <SuggestionChips questions={suggestedQuestions} onClick={sendText} />
        )}

        {interaction && (
          <div className="msg-row agent">
            <div className="msg-avatar agent-avatar">
              <svg viewBox="0 0 20 20" fill="none" width="18" height="18">
                <path
                  d="M10 2C5.58 2 2 5.36 2 9.5c0 2.1.9 4 2.34 5.35L3.5 18l3.5-1.75c.93.31 1.93.5 2.99.5 4.42 0 8-3.36 8-7.5S14.42 2 10 2z"
                  fill="white"
                  fillOpacity="0.9"
                />
              </svg>
            </div>
            <div className="msg-agent-content">
              <div className="msg-agent-name">电商智能客服</div>
              <div className="msg-agent-bubble">
                <UserInteractionCard interaction={interaction} onSubmit={handleRespond} />
              </div>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Input area */}
      <div className="input-area">
        <div className={`input-box${isThinking || interaction ? ' disabled' : ''}`}>
          <textarea
            ref={textareaRef}
            className="input-textarea"
            placeholder={
              isThinking
                ? 'AI 正在处理，请稍候...'
                : isHumanMode
                ? '人工客服服务中，请输入您的问题...'
                : interaction
                ? '请先回答上方问题'
                : '输入您的问题，按 Enter 发送，Shift+Enter 换行'
            }
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isThinking || !!interaction}
            rows={1}
          />
          <button
            className="send-btn"
            onClick={isThinking ? handleInterrupt : handleSend}
            disabled={!!interaction || (!isThinking && !inputText.trim())}
            title={isThinking ? '停止生成' : '发送 (Enter)'}
          >
            {isThinking ? (
              <svg viewBox="0 0 20 20" fill="none" width="18" height="18">
                <rect x="5" y="5" width="10" height="10" rx="1.5" fill="currentColor" />
              </svg>
            ) : (
              <svg viewBox="0 0 20 20" fill="none" width="18" height="18">
                <path
                  d="M4 10L16 4l-4 6 4 6L4 10z"
                  fill="currentColor"
                />
              </svg>
            )}
          </button>
        </div>
        <div className="input-hint">Enter 发送 · Shift+Enter 换行 · 支持中英文</div>
      </div>
    </div>
  );
}
