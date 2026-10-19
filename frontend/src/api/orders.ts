import { useAuthStore } from '../store/authStore';
import { getApiBase } from './config';

function authHeaders(): Record<string, string> {
  const token = useAuthStore.getState().token;
  return token ? { Authorization: token } : {};
}

/** 单条订单记录（由预订记录平铺而来） */
export interface MyOrderBooking {
  bookingId: string;
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
  bookedAt: number | null;
  /** 第三方平台订单详情页 URL，为 null 时不展示跳转入口 */
  orderUrl: string | null;
}

/** 拉取当前登录用户的全部订单（平铺预订记录，按下单时间倒序） */
export async function fetchMyOrders(): Promise<MyOrderBooking[]> {
  const res = await fetch(`${getApiBase()}/api/my-orders/bookings`, {
    headers: authHeaders(),
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 删除指定订单记录（逻辑删除） */
export async function deleteBooking(bookingId: string): Promise<{ deleted: boolean }> {
  const res = await fetch(`${getApiBase()}/api/my-orders/bookings/${bookingId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED');
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 取消指定订单（同步调用第三方平台取消接口 + 更新内部状态为 CANCELLED） */
export async function cancelBooking(
  bookingId: string,
  reason?: string,
): Promise<{ cancelled: boolean; message: string }> {
  const res = await fetch(`${getApiBase()}/api/my-orders/bookings/${bookingId}/cancel`, {
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
