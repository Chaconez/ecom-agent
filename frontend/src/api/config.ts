/**
 * 集中管理后端 API 地址，支持本地集群轮询模式。
 *
 * 使用方式：
 * 1. 在 frontend/.env.local 中配置多个后端地址（逗号分隔）：
 *    VITE_CLUSTER_URLS=http://localhost:8080,http://localhost:8081,http://localhost:8082
 *
 * 2. 在侧边栏开启「集群轮询」开关后，每次 API 请求自动轮询下一个地址。
 *
 * 3. 未配置 VITE_CLUSTER_URLS 时，降级使用 VITE_API_BASE（默认 http://localhost:8080）。
 *
 * VITE_API_BASE 三态语义（构建期烘入，改后需重新 build）：
 * - 未设置：本地开发默认 http://localhost:8080
 * - 设置为空串（VITE_API_BASE=）：**同源模式**，运行时取 window.location.origin。
 *   用于域名 + 反代部署（如 n.chacone.fun → nginx → 后端），构建产物不烘死任何
 *   主机地址，任意域名/端口访问都能把 /api 请求打到同源服务上。
 * - 设置为具体地址（如 http://192.168.3.3:8094）：直连该后端，
 *   仅限调用方能访问该地址的场景（纯内网部署）。
 */

import { useUiStore } from '../store/uiStore';

/**
 * 解析单实例模式下的默认后端地址。
 * 同源模式（显式空串）让部署不再依赖构建机所在网络——这是移动端/外网访问的关键。
 */
function resolveDefaultBase(): string {
  const raw = import.meta.env.VITE_API_BASE;
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    if (trimmed !== '') {
      return trimmed;
    }
    // 显式空串 = 同源模式：跟随页面地址，由反向代理转发 /api
    if (typeof window !== 'undefined' && window.location?.origin) {
      return window.location.origin;
    }
  }
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
