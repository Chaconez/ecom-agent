import { useEffect, useState } from 'react';
import { useAuthStore } from './store/authStore';
import { fetchUserInfo, logout } from './api/auth';
import { userPortalUrl } from './api/config';
import LoginPage from './pages/LoginPage';
import Workbench from './pages/Workbench';

export default function App() {
  const { token, clearAuth } = useAuthStore();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn());

  // 页面刷新/首次加载：校验本地 token 有效性
  const [checking, setChecking] = useState(!!token);

  useEffect(() => {
    if (!token) {
      setChecking(false);
      return;
    }
    fetchUserInfo(token)
      .catch(() => {
        clearAuth();
      })
      .finally(() => setChecking(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // token 失效时统一登出
  const handleUnauthorized = async () => {
    await logout(token ?? '');
    clearAuth();
  };

  if (checking) return null;

  if (!isLoggedIn) {
    return <LoginPage />;
  }

  return <Workbench onUnauthorized={handleUnauthorized} userPortalUrl={userPortalUrl()} />;
}
