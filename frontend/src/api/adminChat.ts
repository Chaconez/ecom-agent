import { useAuthStore } from '../store/authStore';
import { getApiBase } from './config';

/**
 * 客服工作台（管理端）会话接口。
 *
 * <p>对应后端 `AdminChatController`（`/api/admin/conversations/**`），
 * 全部接口要求 `role = ADMIN`，非管理员返回 403。前端统一入口下，
 * admin 账号登录后即可直接调用，无需另开独立管理端。</p>
 */

/** 从 authStore 读取 token，构造带鉴权的请求头（Sa-Token 裸 token，无 Bearer 前缀） */
function authHeaders(): Record<string, string> {
  const token = useAuthStore.getState().token;
  return token ? { Authorization: token } : {};
}

export type ConversationMode = 'AI' | 'HUMAN';

/** 会话列表视图（客服工作台左栏） */
export interface AdminConversation {
  conversationId: string;
  userId: string;
  title: string;
  mode: ConversationMode;
  preview: string;
  previewRole: string;
  updatedAt: number;
}

/** 消息视图（回放与人工对话共用） */
export interface AdminMessage {
  id: string;
  role: 'user' | 'agent' | 'system';
  content: string;
  agentName?: string;
  timestamp: number;
  extra?: Record<string, unknown>;
}

/** 统一把 HTTP 状态码映射为前端可判别的错误标识 */
function toError(res: Response, fallback: string): Error {
  if (res.status === 401) return new Error('UNAUTHORIZED');
  if (res.status === 403) return new Error('FORBIDDEN');
  if (res.status === 404) return new Error('会话不存在');
  return new Error(fallback);
}

/** 查询全部会话（按最后更新时间倒序） */
export async function fetchAdminConversations(): Promise<AdminConversation[]> {
  const res = await fetch(`${getApiBase()}/api/admin/conversations`, {
    headers: authHeaders(),
  });
  if (!res.ok) throw toError(res, `HTTP ${res.status}`);
  return res.json();
}

/** 回放指定会话的全部消息 */
export async function fetchAdminMessages(conversationId: string): Promise<AdminMessage[]> {
  const res = await fetch(
    `${getApiBase()}/api/admin/conversations/${encodeURIComponent(conversationId)}/messages`,
    { headers: authHeaders() },
  );
  if (!res.ok) throw toError(res, `HTTP ${res.status}`);
  return res.json();
}

/** 接管会话（切 HUMAN 模式，AI 停止响应） */
export async function takeoverConversation(conversationId: string): Promise<void> {
  const res = await fetch(
    `${getApiBase()}/api/admin/conversations/${encodeURIComponent(conversationId)}/takeover`,
    { method: 'POST', headers: authHeaders() },
  );
  if (!res.ok) throw toError(res, `HTTP ${res.status}`);
}

/** 结束接管（切回 AI 模式，智能助手恢复响应） */
export async function releaseConversation(conversationId: string): Promise<void> {
  const res = await fetch(
    `${getApiBase()}/api/admin/conversations/${encodeURIComponent(conversationId)}/release`,
    { method: 'POST', headers: authHeaders() },
  );
  if (!res.ok) throw toError(res, `HTTP ${res.status}`);
}

/** 人工客服发言（落库 + 尝试推送到 C 端在线 SSE，不在线靠轮询兜底） */
export async function sendHumanMessage(
  conversationId: string,
  content: string,
): Promise<{ messageId: string }> {
  const res = await fetch(
    `${getApiBase()}/api/admin/conversations/${encodeURIComponent(conversationId)}/human-message`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ content }),
    },
  );
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    if (res.status === 400 && data?.message) throw new Error(data.message);
    throw toError(res, data?.message || `HTTP ${res.status}`);
  }
  return res.json();
}
