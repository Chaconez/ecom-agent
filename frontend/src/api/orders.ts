import { useAuthStore } from '../store/authStore';
import { getApiBase } from './config';

function authHeaders(): Record<string, string> {
  const token = useAuthStore.getState().token;
  return token ? { Authorization: token } : {};
}

/** 单条订单记录（电商订单平铺视图） */
export interface MyOrder {
  orderNo: string;
  bizType: string | null;
  bizTypeLabel: string | null;
  title: string | null;
  status: string | null;
  statusLabel: string | null;
  externalStatus: string | null;
  totalAmount: string | null;
  currency: string | null;
  platform: string | null;
  externalOrderNo: string | null;
  paymentStatus: string | null;
  startTime: number | null;
  endTime: number | null;
  orderedAt: number | null;
  /** 第三方平台订单详情页 URL，为 null 时不展示跳转入口 */
  orderUrl: string | null;
}

/** 拉取当前登录用户的全部订单（按下单时间倒序） */
export async function fetchMyOrders(): Promise<MyOrder[]> {
  const res = await fetch(`${getApiBase()}/api/my-orders/orders`, {
    headers: authHeaders(),
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 删除指定订单（逻辑删除） */
export async function deleteOrder(orderNo: string): Promise<{ deleted: boolean }> {
  const res = await fetch(`${getApiBase()}/api/my-orders/orders/${orderNo}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 取消指定订单（同步调用渠道取消接口 + 更新内部状态为 CANCELLED） */
export async function cancelOrder(
  orderNo: string,
  reason?: string,
): Promise<{ cancelled: boolean; message: string }> {
  const res = await fetch(`${getApiBase()}/api/my-orders/orders/${orderNo}/cancel`, {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify(reason ? { reason } : {}),
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `HTTP ${res.status}`);
  }
  return res.json();
}
