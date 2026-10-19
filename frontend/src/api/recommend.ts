import { useAuthStore } from '../store/authStore';
import { getApiBase } from './config';

function authHeaders(): Record<string, string> {
  const token = useAuthStore.getState().token;
  return token ? { Authorization: token } : {};
}

/** 推荐商品项（含 LLM 生成的个性化推荐理由） */
export interface RecommendItem {
  productId: string;
  name: string;
  category: string;
  brand: string | null;
  price: string | null;
  originalPrice: string | null;
  tags: string | null;
  rating: string | null;
  sales: number | null;
  imageEmoji: string | null;
  description: string | null;
  /** 个性化推荐理由（LLM 生成，失败时为规则兜底文案） */
  reason: string;
  matchScore: number;
}

/** 推荐列表响应 */
export interface RecommendResponse {
  userId: string;
  /** 用户是否已配置偏好（false 时前端引导去偏好设置） */
  hasPreferences: boolean;
  items: RecommendItem[];
}

/** 拉取当前登录用户的个性化推荐商品 */
export async function fetchRecommendations(limit = 8): Promise<RecommendResponse> {
  const res = await fetch(`${getApiBase()}/api/recommendations?limit=${limit}`, {
    headers: authHeaders(),
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
