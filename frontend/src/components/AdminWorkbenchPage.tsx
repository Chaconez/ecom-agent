import { useEffect, useState } from 'react';
import AdminConversationPanel from './AdminConversationPanel';
import AdminApprovalPanel from './AdminApprovalPage';
import { fetchApprovals } from '../api/admin';

type WorkbenchTab = 'chat' | 'approval';

/**
 * 客服工作台（管理端功能，统一入口内呈现）。
 *
 * <p>只有 `role = ADMIN` 的账号会看到入口（侧边栏「🎧 客服工作台」），
 * 两个 tab：会话接管（人工应答「转人工」会话）与工单审批。
 * 后端接口全部要求 `@SaCheckRole("ADMIN")`，非管理员进入会收到 403。</p>
 */
export default function AdminWorkbenchPage() {
  const [tab, setTab] = useState<WorkbenchTab>('chat');
  const [pendingCount, setPendingCount] = useState(0);

  // 初次进入先拉一次待处理数，让工单 tab 的红点在未切换前就能显示
  useEffect(() => {
    fetchApprovals('PENDING')
      .then((list) => setPendingCount(list.length))
      .catch(() => {
        // 403（非管理员）/ 网络异常：不显示红点即可，不打扰主流程
      });
  }, []);

  return (
    <div className="aw-page">
      <header className="aw-header">
        <div className="aw-header-title">
          <h1 className="aw-title">客服工作台</h1>
          <p className="aw-subtitle">接管会话 · 人工应答 · 工单审批</p>
        </div>
        <div className="aw-tabs">
          <button
            className={`aw-tab${tab === 'chat' ? ' active' : ''}`}
            onClick={() => setTab('chat')}
          >
            💬 会话
          </button>
          <button
            className={`aw-tab${tab === 'approval' ? ' active' : ''}`}
            onClick={() => setTab('approval')}
          >
            📋 工单
            {pendingCount > 0 && <span className="aw-tab-count">{pendingCount}</span>}
          </button>
        </div>
      </header>

      {tab === 'chat' ? (
        <AdminConversationPanel />
      ) : (
        <AdminApprovalPanel onPendingCount={setPendingCount} />
      )}
    </div>
  );
}
