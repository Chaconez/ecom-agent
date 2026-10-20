import { useAuthStore } from '../store/authStore';
import { getApiBase } from './config';

/** 从 authStore 读取 token，构造带鉴权的请求头 */
function authHeaders(): Record<string, string> {
  const token = useAuthStore.getState().token;
  return token ? { Authorization: token } : {};
}

export type ConversationMode = 'AI' | 'HUMAN';

/** 管理端会话列表视图（客服工作台） */
export interface AdminConversation {
  conversationId: string;
  userId: string;
  title: string;
  mode: ConversationMode;
  preview: string;
  previewRole: string;
  updatedAt: number;
}

/** 消息视图（回放/人工对话共用） */
export interface AdminMessage {
  id: string;
  role: 'user' | 'agent' | 'system';
  content: string;
  agentName?: string;
  timestamp: number;
  extra?: Record<string, any>;
}

/** 查询全部会话（按最后更新时间倒序） */
export async function fetchAdminConversations(): Promise<AdminConversation[]> {
  const res = await fetch(`${getApiBase()}/api/admin/conversations`, {
    headers: authHeaders(),
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (res.status === 403) throw new Error('FORBIDDEN');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 回放指定会话的全部消息 */
export async function fetchAdminMessages(conversationId: string): Promise<AdminMessage[]> {
  const res = await fetch(
    `${getApiBase()}/api/admin/conversations/${encodeURIComponent(conversationId)}/messages`,
    { headers: authHeaders() },
  );
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (res.status === 403) throw new Error('FORBIDDEN');
  if (res.status === 404) throw new Error('会话不存在');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 接管会话（切换 HUMAN 模式） */
export async function takeoverConversation(conversationId: string): Promise<void> {
  const res = await fetch(
    `${getApiBase()}/api/admin/conversations/${encodeURIComponent(conversationId)}/takeover`,
    { method: 'POST', headers: authHeaders() },
  );
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (res.status === 403) throw new Error('FORBIDDEN');
  if (res.status === 404) throw new Error('会话不存在');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
}

/** 结束接管（切回 AI 模式） */
export async function releaseConversation(conversationId: string): Promise<void> {
  const res = await fetch(
    `${getApiBase()}/api/admin/conversations/${encodeURIComponent(conversationId)}/release`,
    { method: 'POST', headers: authHeaders() },
  );
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (res.status === 403) throw new Error('FORBIDDEN');
  if (res.status === 404) throw new Error('会话不存在');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
}

/** 人工客服发言 */
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
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (res.status === 403) throw new Error('FORBIDDEN');
  if (res.status === 404) throw new Error('会话不存在');
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || `HTTP ${res.status}`);
  }
  return res.json();
}
