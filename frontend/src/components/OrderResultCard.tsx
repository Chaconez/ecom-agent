import type { OrderResult } from '../types';

// 业务类型对应的图标
function BizIcon({ bizType }: { bizType: OrderResult['bizType'] }) {
  if (bizType === 'goods') {
    return (
      <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
        <path
          d="M7 4h10a1 1 0 0 1 1 1v1h2a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h2V5a1 1 0 0 1 1-1zm1 2h8V5H8v1zm-3 3v11h14V9H5zm3 2h8v2H8v-2z"
          fill="currentColor"
        />
      </svg>
    );
  }
  if (bizType === 'service') {
    return (
      <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
        <path
          d="M12 2a9 9 0 0 0-9 9v5a3 3 0 0 0 3 3h2v-8H6v-1a6 6 0 1 1 12 0v1h-2v8h2a3 3 0 0 0 3-3v-5a9 9 0 0 0-9-9zm-2 10h4v9h-4v-9z"
          fill="currentColor"
        />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
      <path d="M4 4h16v4H4V4zm0 6h16v10H4V10z" fill="currentColor" />
    </svg>
  );
}

/**
 * 下单成功卡片：展示订单号、标题、金额，并提供“立即支付”按钮跳转支付链接。
 * 支付链接缺失时按钮不可用，仅展示订单信息。
 */
export default function OrderResultCard({ data }: { data: OrderResult }) {
  const hasPayUrl = !!(data.payUrl && data.payUrl.trim());
  const currencySymbol = data.currency === 'USD' ? '$' : '¥';

  return (
    <div className="order-result-card">
      <div className="order-result-card-header">
        <span className="order-result-card-icon">
          <BizIcon bizType={data.bizType} />
        </span>
        <div className="order-result-card-heading">
          <span className="order-result-card-type">{data.bizTypeLabel || '商品'}下单成功</span>
          {data.title && <span className="order-result-card-title">{data.title}</span>}
        </div>
        <span className="order-result-card-status">{data.statusLabel || '待支付'}</span>
      </div>

      <div className="order-result-card-body">
        <div className="order-result-card-row">
          <span className="order-result-card-label">订单号</span>
          <span className="order-result-card-value order-no">{data.orderId}</span>
        </div>
        {data.amount && (
          <div className="order-result-card-row">
            <span className="order-result-card-label">应付金额</span>
            <span className="order-result-card-amount">
              {currencySymbol}
              {data.amount}
            </span>
          </div>
        )}
      </div>

      <div className="order-result-card-footer">
        {hasPayUrl ? (
          <a
            className="order-result-card-pay-btn"
            href={data.payUrl!}
            target="_blank"
            rel="noopener noreferrer"
          >
            立即支付
          </a>
        ) : (
          <span className="order-result-card-pay-btn disabled">支付链接暂不可用</span>
        )}
      </div>
    </div>
  );
}
