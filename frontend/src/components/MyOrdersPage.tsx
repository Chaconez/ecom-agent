import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  fetchMyOrders,
  deleteOrder,
  cancelOrder,
  type MyOrder,
} from '../api/orders';
import { useAuthStore } from '../store/authStore';
import { useChatStore } from '../store/chatStore';
import { useUiStore } from '../store/uiStore';

/**
 * 「我的订单」页面：基于电商订单表（ecommerce_order）平铺展示当前登录用户的订单。
 * <p>样式类统一使用 orders-* 体系（见 App.css），与结果卡片的结果集样式区分。</p>
 */

/** 订单状态筛选 tab */
const STATUS_TABS: { label: string; value: string }[] = [
  { label: '全部', value: '' },
  { label: '待付款', value: 'unpaid' },
  { label: '已确认', value: 'confirmed' },
  { label: '已完成', value: 'completed' },
  { label: '已取消', value: 'cancelled' },
];

/** 筛选分组 → 订单状态码集合 */
const STATUS_GROUPS: Record<string, Set<string>> = {
  unpaid: new Set(['CREATED', 'PENDING_PAYMENT']),
  confirmed: new Set(['PAID', 'CONFIRMED']),
  completed: new Set(['COMPLETED']),
  cancelled: new Set(['CANCELLED', 'REFUNDED', 'FAILED']),
};

/** 订单状态徽章配色，复用 admin-status 的样式类 */
const STATUS_CLASS: Record<string, string> = {
  CREATED: 'pending',
  PENDING_PAYMENT: 'pending',
  PAID: 'approved',
  CONFIRMED: 'approved',
  COMPLETED: 'approved',
  CANCELLED: 'cancelled',
  REFUNDED: 'cancelled',
  FAILED: 'rejected',
};

/** 支付状态码 → 中文展示 */
const PAYMENT_STATUS_LABELS: Record<string, string> = {
  UNPAID: '未支付',
  PAID: '已支付',
  PAYING: '支付中',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  CLOSED: '已关闭',
};

/** 允许取消的订单状态（未终态） */
const CANCELLABLE_STATUSES = new Set(['CREATED', 'PENDING_PAYMENT', 'PAID', 'CONFIRMED']);

function canCancel(b: MyOrder): boolean {
  return !!b.status && CANCELLABLE_STATUSES.has(b.status);
}

function formatTime(ts: number | null): string {
  if (!ts) return '-';
  return new Date(ts).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatDateTime(ts: number | null): string {
  if (!ts) return '-';
  return new Date(ts).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** 生成快捷动作对应的对话提问，尽量把订单上下文塞给智能体 */
function buildPrompts(b: MyOrder) {
  const no = b.externalOrderNo || b.orderNo;
  const name = b.title || b.bizTypeLabel || '订单';
  return {
    logistics: `请帮我查询订单 ${no}（${name}）的物流进度`,
    aftersale: `我要为订单 ${no}（${name}）申请售后，请帮我处理`,
    consult: `我想咨询订单 ${no}（${name}）的相关问题，请帮我解答`,
  };
}

function StatusBadge({
  code,
  label,
  classMap,
}: {
  code: string | null;
  label: string | null;
  classMap: Record<string, string>;
}) {
  if (!code) return null;
  const cls = classMap[code] || 'pending';
  return <span className={`admin-status admin-status-${cls}`}>{label || code}</span>;
}

function OrderCard({
  order,
  active,
  onClick,
}: {
  order: MyOrder;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <div className={`orders-card${active ? ' active' : ''}`} onClick={onClick}>
      <div className="orders-card-head">
        <span className="orders-card-route">{order.bizTypeLabel || '订单'}</span>
        {order.platform && <span className="orders-card-tag">{order.platform}</span>}
      </div>
      <div className="orders-card-title">{order.title || order.externalOrderNo || order.orderNo}</div>
      <div className="orders-card-meta">
        <StatusBadge code={order.status} label={order.statusLabel} classMap={STATUS_CLASS} />
        {order.totalAmount && (
          <span className="orders-card-count">
            {order.currency || '¥'} {order.totalAmount}
          </span>
        )}
      </div>
      <div className="orders-card-time">下单：{formatTime(order.orderedAt)}</div>
    </div>
  );
}

function OrderDetail({
  order,
  onAction,
  onRefresh,
}: {
  order: MyOrder;
  onAction: (prompt: string) => void;
  onRefresh: () => void;
}) {
  const prompts = useMemo(() => buildPrompts(order), [order]);

  /** 删除订单记录，确认后调用后端接口并刷新列表 */
  const handleDelete = async () => {
    if (
      !window.confirm(
        '删除操作仅移除本地记录，不会取消第三方平台的订单。\n建议先点击「取消订单」同步取消平台订单后，再执行删除。\n\n确定仍要删除该订单记录吗？',
      )
    )
      return;
    try {
      await deleteOrder(order.orderNo);
      onRefresh();
    } catch (err: any) {
      if (err?.message === 'UNAUTHORIZED') {
        alert('登录已过期，请重新登录');
        return;
      }
      alert('删除失败，请稍后重试');
    }
  };

  /** 取消订单：调用后端取消接口（同步取消第三方平台），完成后刷新列表 */
  const handleCancel = async () => {
    if (!window.confirm('确定要取消该订单吗？取消后将同步取消第三方平台的订单。')) return;
    try {
      const result = await cancelOrder(order.orderNo);
      alert(result.message || '取消成功');
      onRefresh();
    } catch (err: any) {
      if (err?.message === 'UNAUTHORIZED') {
        alert('登录已过期，请重新登录');
        return;
      }
      alert(err?.message || '取消订单失败，请稍后重试');
    }
  };

  return (
    <div className="orders-detail">
      <div className="orders-detail-head">
        <div>
          <div className="orders-detail-title">
            {order.title || order.bizTypeLabel || '订单'}
            {order.platform && <span className="orders-card-tag">{order.platform}</span>}
          </div>
          <div className="orders-detail-sub">
            订单号：{order.orderNo}
            {order.externalOrderNo && (
              <>
                <span className="orders-detail-sep">·</span>
                平台单号：{order.externalOrderNo}
              </>
            )}
          </div>
        </div>
        <div className="orders-detail-status">
          <StatusBadge code={order.status} label={order.statusLabel} classMap={STATUS_CLASS} />
        </div>
      </div>

      <div className="orders-detail-block">
        <div className="orders-detail-label">订单信息</div>
        <div className="orders-item-meta">
          {order.bizTypeLabel && <span>类型：{order.bizTypeLabel}</span>}
          {order.totalAmount && (
            <span>
              金额：{order.currency || '¥'} {order.totalAmount}
            </span>
          )}
          {order.paymentStatus && (
            <span>支付状态：{PAYMENT_STATUS_LABELS[order.paymentStatus] || order.paymentStatus}</span>
          )}
          {order.startTime && (
            <span>
              时间：{formatDateTime(order.startTime)}
              {order.endTime ? ` → ${formatDateTime(order.endTime)}` : ''}
            </span>
          )}
          <span>下单时间：{formatTime(order.orderedAt)}</span>
        </div>
      </div>

      <div className="orders-actions">
        {order.orderUrl && (
          <a
            className="orders-action-btn"
            href={order.orderUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            🔗 查看平台订单
          </a>
        )}
        <button className="orders-action-btn" onClick={() => onAction(prompts.logistics)}>
          🚚 查物流
        </button>
        <button className="orders-action-btn" onClick={() => onAction(prompts.aftersale)}>
          🛠 申请售后
        </button>
        <button className="orders-action-btn primary" onClick={() => onAction(prompts.consult)}>
          💬 再次咨询
        </button>
        {canCancel(order) && (
          <button className="orders-action-btn danger" onClick={handleCancel}>
            ✕ 取消订单
          </button>
        )}
        <button className="orders-action-btn" onClick={handleDelete}>
          🗑 删除记录
        </button>
      </div>
    </div>
  );
}

export default function MyOrdersPage() {
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const setView = useUiStore((s) => s.setView);
  const setPendingUserMessage = useUiStore((s) => s.setPendingUserMessage);
  const createConversation = useChatStore((s) => s.createConversation);

  const [orders, setOrders] = useState<MyOrder[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [statusTab, setStatusTab] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await fetchMyOrders();
      setOrders(data);
      setSelectedId((prev) => {
        if (prev && data.some((o) => o.orderNo === prev)) return prev;
        return data.length > 0 ? data[0].orderNo : null;
      });
    } catch (err: any) {
      if (err?.message === 'UNAUTHORIZED') {
        clearAuth();
        return;
      }
      setError('加载订单失败');
      setOrders([]);
      setSelectedId(null);
    } finally {
      setLoading(false);
    }
  }, [clearAuth]);

  useEffect(() => {
    load();
  }, [load]);

  /** 按状态 tab 过滤（全部数据已在一页内，前端过滤即可） */
  const filtered = useMemo(() => {
    if (!statusTab) return orders;
    const group = STATUS_GROUPS[statusTab];
    return orders.filter((o) => o.status && group.has(o.status));
  }, [orders, statusTab]);

  const selected = selectedId ? filtered.find((o) => o.orderNo === selectedId) : undefined;

  /** 快捷动作：新建对话 → 切到对话页 → 预填 message，由 ChatWindow 自动发送 */
  const handleAction = (prompt: string) => {
    createConversation();
    setPendingUserMessage(prompt);
    setView('chat');
  };

  return (
    <main className="admin-page orders-page">
      <div className="admin-header">
        <div>
          <h1 className="admin-title">我的订单</h1>
          <p className="admin-subtitle">查看您的订单、支付与售后信息，快捷发起咨询</p>
        </div>
        <button className="admin-refresh" onClick={load} disabled={loading}>
          刷新
        </button>
      </div>

      <div className="admin-tabs">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value || 'all'}
            className={`admin-tab${statusTab === tab.value ? ' active' : ''}`}
            onClick={() => setStatusTab(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {error && <div className="admin-error">{error}</div>}

      {loading ? (
        <div className="admin-empty">加载中...</div>
      ) : filtered.length === 0 ? (
        <div className="admin-empty">您还没有订单，去对话页咨询客服吧</div>
      ) : (
        <div className="orders-layout">
          <div className="orders-list">
            {filtered.map((o) => (
              <OrderCard
                key={o.orderNo}
                order={o}
                active={selectedId === o.orderNo}
                onClick={() => setSelectedId(o.orderNo)}
              />
            ))}
          </div>
          <div className="orders-main">
            {selected ? (
              <OrderDetail order={selected} onAction={handleAction} onRefresh={load} />
            ) : (
              <div className="admin-empty">请选择左侧的订单查看详情</div>
            )}
          </div>
        </div>
      )}
    </main>
  );
}
