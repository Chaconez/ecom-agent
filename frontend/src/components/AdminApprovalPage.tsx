import { useCallback, useEffect, useState } from 'react';
import {
  fetchApprovals,
  decideApproval,
  type AdminApproval,
  type ApprovalForm,
  type ApprovalStatusFilter,
} from '../api/admin';
import { useAuthStore } from '../store/authStore';

const STATUS_TABS: { label: string; value: ApprovalStatusFilter }[] = [
  { label: '待处理', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '全部', value: '' },
];

const STATUS_CLASS: Record<string, string> = {
  PENDING: 'pending',
  APPROVED: 'approved',
  REJECTED: 'rejected',
  CANCELLED: 'cancelled',
};

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

/** 提取审批表单结构化字段（若为字符串则原样返回 null） */
function asForm(form: AdminApproval['form']): ApprovalForm | null {
  if (form && typeof form === 'object') return form as ApprovalForm;
  return null;
}

function FormField({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <div className="admin-form-field">
      <span className="admin-form-label">{label}</span>
      <span className="admin-form-value">{value}</span>
    </div>
  );
}

function ApprovalCard({
  item,
  onDecide,
  busy,
}: {
  item: AdminApproval;
  onDecide: (id: string, decision: 'agree' | 'refuse', remark: string) => void;
  busy: boolean;
}) {
  const [remark, setRemark] = useState('');
  const form = asForm(item.form);
  const isPending = item.status === 'PENDING';

  return (
    <div className="admin-card">
      <div className="admin-card-head">
        <div className="admin-card-title">{item.title || '（无标题）'}</div>
        <span className={`admin-status admin-status-${STATUS_CLASS[item.status] || 'pending'}`}>
          {item.statusLabel || item.status}
        </span>
      </div>

      <div className="admin-card-meta">
        <span>申请人：{item.userName || item.userId}</span>
        <span>提交：{formatTime(item.submitTime)}</span>
        {item.orderId && <span>关联单号：{item.orderId}</span>}
      </div>

      {form && (
        <div className="admin-form-grid">
          <FormField label="商品名称" value={form.productName} />
          <FormField label="购买日期" value={form.purchaseDate} />
          <FormField label="问题描述" value={form.issueDesc} />
        </div>
      )}

      {item.remark && (
        <div className="admin-card-remark">处理备注：{item.remark}</div>
      )}

      {isPending && (
        <div className="admin-card-actions">
          <input
            className="admin-remark-input"
            placeholder="处理备注（可选）"
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
            disabled={busy}
          />
          <button
            className="admin-btn admin-btn-approve"
            disabled={busy}
            onClick={() => onDecide(item.processInstanceId, 'agree', remark)}
          >
            通过
          </button>
          <button
            className="admin-btn admin-btn-reject"
            disabled={busy}
            onClick={() => onDecide(item.processInstanceId, 'refuse', remark)}
          >
            拒绝
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * 客服工作台 · 工单审批面板。
 *
 * <p>作为「客服工作台」的一个 tab 内嵌渲染，页面标题与 tab 切换由外层
 * AdminWorkbenchPage 提供，本组件只负责状态筛选、列表与决策。</p>
 */
export default function AdminApprovalPanel({
  onPendingCount,
}: {
  /** 待处理工单数上报（供外层 tab 显示红点） */
  onPendingCount?: (count: number) => void;
}) {
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [status, setStatus] = useState<ApprovalStatusFilter>('PENDING');
  const [list, setList] = useState<AdminApproval[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(
    async (s: ApprovalStatusFilter) => {
      setLoading(true);
      setError('');
      try {
        const data = await fetchApprovals(s);
        setList(data);
        // 仅在「待处理」筛选下上报数量，避免其他筛选覆盖红点
        if (s === 'PENDING') onPendingCount?.(data.length);
      } catch (err: any) {
        if (err?.message === 'UNAUTHORIZED') {
          clearAuth();
          return;
        }
        setError(err?.message === 'FORBIDDEN' ? '无权访问客服工作台' : '加载工单失败');
        setList([]);
      } finally {
        setLoading(false);
      }
    },
    [clearAuth, onPendingCount],
  );

  useEffect(() => {
    load(status);
  }, [status, load]);

  const handleDecide = async (id: string, decision: 'agree' | 'refuse', remark: string) => {
    setBusyId(id);
    setError('');
    try {
      await decideApproval(id, decision, remark);
      await load(status);
    } catch (err: any) {
      if (err?.message === 'UNAUTHORIZED') {
        clearAuth();
        return;
      }
      setError(err?.message || '操作失败');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="aw-approval">
      <div className="aw-approval-toolbar">
        <div className="aw-approval-title-group">
          <span className="aw-panel-title">工单队列</span>
          <span className="aw-panel-sub">查看并处理用户服务工单</span>
        </div>
        <button className="aw-refresh" onClick={() => load(status)} disabled={loading}>
          刷新
        </button>
      </div>

      <div className="admin-tabs">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value || 'all'}
            className={`admin-tab${status === tab.value ? ' active' : ''}`}
            onClick={() => setStatus(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {error && <div className="admin-error">{error}</div>}

      <div className="admin-list">
        {loading ? (
          <div className="admin-empty">加载中...</div>
        ) : list.length === 0 ? (
          <div className="admin-empty">暂无工单</div>
        ) : (
          list.map((item) => (
            <ApprovalCard
              key={item.processInstanceId}
              item={item}
              onDecide={handleDecide}
              busy={busyId === item.processInstanceId}
            />
          ))
        )}
      </div>
    </div>
  );
}
