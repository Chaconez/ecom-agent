import { useEffect, useState } from 'react';
import { useAuthStore } from './store/authStore';
import { useUiStore } from './store/uiStore';
import { fetchUserInfo } from './api/auth';
import Sidebar from './components/Sidebar';
import ChatWindow from './components/ChatWindow';
import AdminWorkbenchPage from './components/AdminWorkbenchPage';
import MyOrdersPage from './components/MyOrdersPage';
import RecommendPage from './components/RecommendPage';
import PreferencePage from './components/PreferencePage';
import LoginPage from './components/LoginPage';
import './App.css';

export default function App() {
  const { token, clearAuth, setIsAdmin } = useAuthStore();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn());
  const isAdmin = useAuthStore((s) => s.isAdmin);
  const view = useUiStore((s) => s.view);
  const setView = useUiStore((s) => s.setView);

  /**
   * 页面刺新/首次加载时，若 localStorage 存有 token，则立即对后端进行驗证。
   * - 驗证通过（200）：保持已登录状态，正常展示主界面。
   * - 驗证失败（401 / 其他错误）：清除本地 token，跳转登录页。
   * checking 状态技巧：仅当 token 存在时才请求后端，不存在时直接展示登录页。
   */
  const [checking, setChecking] = useState(!!token);

  useEffect(() => {
    if (!token) {
      // 本地无 token，无需骗证接口，直接进入登录页
      setChecking(false);
      return;
    }
    // 调用 GET /api/auth/info 驗证 token 是否仍有效
    fetchUserInfo(token)
      .then((info) => {
        // 同步最新的管理员标识
        setIsAdmin(info.admin === true);
      })
      .catch(() => {
        // Token 已失效（401）或网络异常：清除本地登录态，跳入登录页
        clearAuth();
      })
      .finally(() => setChecking(false));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 仅在组件首次挂载（页面初始化/刷新）时运行一次

  /**
   * 管理端没有「为你推荐」「偏好设置」两个 C 端页面。
   * 若登录态从普通用户切到管理员时残留了这两个视图（同一 SPA 会话内退出再登录），
   * 兜底切回客服工作台，避免渲染出侧边栏已隐藏的页面。
   */
  useEffect(() => {
    if (isAdmin && (view === 'recommend' || view === 'preference')) {
      setView('admin');
    }
  }, [isAdmin, view, setView]);

  // token 正在驗证中：返回空白屏避免闪现错误页面
  if (checking) return null;

  if (!isLoggedIn) {
    return <LoginPage />;
  }

  return (
    <div className="app-root">
      <div className="app-body">
        <Sidebar />
        {view === 'admin' ? (
          <AdminWorkbenchPage />
        ) : view === 'orders' ? (
          <MyOrdersPage />
        ) : view === 'recommend' ? (
          <RecommendPage />
        ) : view === 'preference' ? (
          <PreferencePage />
        ) : (
          <ChatWindow />
        )}
      </div>
    </div>
  );
}
