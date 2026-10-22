/**
 * 集中管理后端 API 地址，支持本地集群轮询模式。
 *
 * 使用方式：
 * 1. 在 frontend/.env.local 中配置多个后端地址（逗号分隔）：
 *    VITE_CLUSTER_URLS=http://localhost:8080,http://localhost:8081,http://localhost:8082
 *
 * 2. 在侧边栏开启「集群轮询」开关后，每次 API 请求自动轮询下一个地址。
 *
 * 3. 未配置 VITE_CLUSTER_URLS 时，降级使用 VITE_API_BASE。
 *
 * VITE_API_BASE 语义（构建期烘入，改后需重新 build）：
 * - **未设置（默认）或显式空串：同源模式**，运行时取 window.location.origin。
 *   这是部署默认值——产物不烘死任何主机地址，nginx 把 /api 反代到后端，
 *   局域网 / 多设备 / 域名访问都能把 /api 打到同源服务上；本地 dev 由
 *   vite.config.ts 的 /api 代理转发到后端。忘记传环境变量也不会打到 localhost。
 * - 设置为具体地址（如 http://192.168.3.3:8094）：直连该后端，
 *   仅限调用方能访问该地址的场景（纯内网调试）。
 */

import { useUiStore } from '../store/uiStore';

/**
 * 解析单实例模式下的默认后端地址。
 */
function resolveDefaultBase(): string {
  const raw = import.meta.env.VITE_API_BASE;
  // 显式配置了非空地址：直连该后端
  if (typeof raw === 'string' && raw.trim() !== '') {
    return raw.trim();
  }
  // 未设置或显式空串 = 同源模式：跟随页面地址，由反向代理 / dev proxy 转发 /api
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin;
  }
  // 兜底：仅非浏览器环境（构建期 / SSR）会走到这里
  return 'http://localhost:8080';
}

const DEFAULT_BASE = resolveDefaultBase();

/** 从环境变量解析集群地址列表 */
function parseClusterUrls(): string[] {
  const raw: string | undefined = import.meta.env.VITE_CLUSTER_URLS;
  if (!raw) return [DEFAULT_BASE];
  return raw
    .split(',')
    .map((u: string) => u.trim())
    .filter(Boolean);
}

const clusterUrls = parseClusterUrls();

/** 轮询游标，每次调用 nextBase() 递增 */
let cursor = 0;

/**
 * 获取当前应使用的后端 API 地址。
 * - 集群模式开启时：每次调用返回下一个地址（轮询）
 * - 集群模式关闭时：始终返回第一个地址
 */
export function getApiBase(): string {
  const clusterMode = useUiStore.getState().clusterMode;
  if (!clusterMode || clusterUrls.length <= 1) {
    return clusterUrls[0] ?? DEFAULT_BASE;
  }
  const base = clusterUrls[cursor % clusterUrls.length];
  cursor = (cursor + 1) % clusterUrls.length;
  return base;
}

/** 获取当前集群地址列表（供 UI 展示） */
export function getClusterUrls(): string[] {
  return [...clusterUrls];
}

/** 集群地址数量是否 > 1（决定是否展示开关） */
export function isClusterAvailable(): boolean {
  return clusterUrls.length > 1;
}
