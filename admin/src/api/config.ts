/**
 * API 基础地址解析：与 C 端同源构建模式保持一致——
 * 生产构建不烘死主机地址，任意域名（含 NAS 外网 IPv6）部署均可直接用。
 */
function resolveDefaultBase(): string {
  const raw = import.meta.env.VITE_API_BASE;
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    if (trimmed !== '') return trimmed;
    if (typeof window !== 'undefined' && window.location?.origin) return window.location.origin;
  }
  return 'http://localhost:8094';
}

export function getApiBase(): string {
  return resolveDefaultBase();
}

/** 用户端入口地址（管理端登录页引导非管理员账号前往） */
export function userPortalUrl(): string {
  if (typeof window === 'undefined') return 'http://localhost:8095';
  const { protocol, hostname } = window.location;
  return `${protocol}//${hostname}:8095`;
}
