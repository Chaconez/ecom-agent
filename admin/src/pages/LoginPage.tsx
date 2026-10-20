import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { login, fetchUserInfo } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import { userPortalUrl } from '../api/config';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notAdmin, setNotAdmin] = useState(false);

  const setAuth = useAuthStore((s) => s.setAuth);
  const clearAuth = useAuthStore((s) => s.clearAuth);

  const handleLogin = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError('请输入用户名和密码');
      return;
    }
    setLoading(true);
    setError('');
    setNotAdmin(false);
    try {
      const result = await login(username.trim(), password.trim());
      const info = await fetchUserInfo(result.token);
      if (info.admin !== true) {
        // 非管理员账号：本地立即清除登录态，引导前往用户端
        clearAuth();
        setNotAdmin(true);
        return;
      }
      setAuth(result.token, info.userId, username.trim(), true);
    } catch (err: any) {
      setError(err.message || '登录失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter') handleLogin();
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-brand">
          <span className="login-brand-icon">🛡️</span>
          <div>
            <div className="login-brand-name">
              电商智能客服
              <span className="portal-badge" title="管理端：客服工作台入口">管理端</span>
            </div>
            <div className="login-brand-sub">客服工作台</div>
          </div>
        </div>

        <h2 className="login-title">管理员登录</h2>
        <p className="login-desc">请使用管理员账号（admin）登录客服工作台</p>

        <form className="login-form" onSubmit={handleLogin}>
          <div className="login-field">
            <label className="login-label">用户名</label>
            <input
              className="login-input"
              type="text"
              placeholder="请输入管理员账号"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onKeyDown={handleKeyDown}
              autoFocus
              autoComplete="username"
              disabled={loading}
            />
          </div>

          <div className="login-field">
            <label className="login-label">密码</label>
            <input
              className="login-input"
              type="password"
              placeholder="请输入密码"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={handleKeyDown}
              autoComplete="current-password"
              disabled={loading}
            />
          </div>

          {error && <div className="login-error">{error}</div>}

          {notAdmin && (
            <div className="login-notice">
              <div>该账号不是管理员，无法进入管理端。</div>
              <div>
                普通用户请前往<b>用户端</b>：
                <a href={userPortalUrl()} target="_blank" rel="noreferrer">
                  {userPortalUrl()}
                </a>
              </div>
            </div>
          )}

          <button
            className="login-btn"
            type="submit"
            disabled={loading || !username.trim() || !password.trim()}
          >
            {loading ? (
              <span className="login-btn-loading">
                <span className="login-spinner" />
                登录中...
              </span>
            ) : (
              '登 录'
            )}
          </button>
        </form>

        <div className="login-hint">
          <div>演示账号：admin &nbsp;·&nbsp; 密码 123456</div>
          <div className="login-hint-sub">user / test 账号请登录用户端（8095 端口）</div>
        </div>
      </div>
      <div className="login-footer">© 2026 章文杰 · 技术演示项目</div>
    </div>
  );
}
