export type TimelineItem =
  | { kind: 'thinking'; agentName: string; text: string }
  | {
      kind: 'tool';
      id: string;
      agentName: string;
      title: string;
      status: 'pending' | 'done' | 'in-progress';
      result?: string;
      /** 工具调用的入参 JSON 字符串 */
      arguments?: string;
    }
  | { kind: 'plan'; tasks: PlanTask[] }
  | { kind: 'result'; data: AgentResultData }
  | { kind: 'order'; data: OrderResult }
  | { kind: 'plan_html'; data: PlanHtmlData };

export interface Message {
  id: string;
  role: 'user' | 'agent' | 'system';
  content: string;
  agentName?: string;
  timestamp: number;
  /** 快照的该轮进度树，用于历史对话中持久展示 */
  progress?: PlanProgress;
  /** 快照的该轮思考过程（每个 Agent 有多轮） */
  thinkingByAgent?: Record<string, string[]>;
  /** 快照的 ItineraryPlanAgent 任务列表 */
  planTasks?: PlanTask[];
  /** 快照的搜索结果卡片 */
  resultData?: AgentResultData[];
  /** 按时间顺序快照的思考/工具/计划/结果时间轴 */
  timeline?: TimelineItem[];
  /** 用户对当前模型回答的反馈：LIKE 点赞 / DISLIKE 点踩 / undefined 未反馈 */
  feedback?: 'LIKE' | 'DISLIKE' | null;
  /** 反馈时间戳（毫秒） */
  feedbackAt?: number;
}

export interface PlanProgress {
  percent: number;
  steps: PlanStep[];
}

export interface PlanStep {
  id: string;
  title: string;
  status: 'pending' | 'done' | 'in-progress';
  /** 该步骤所属的 Agent 名称（tool 步骤需要此字段来归组） */
  agentName?: string;
  /** 工具执行结果文本（可展开查看） */
  result?: string;
  /** 工具调用的入参 JSON 字符串 */
  arguments?: string;
}

/** ItineraryPlanAgent plan-and-execute 规划的单个子任务 */
export interface PlanTask {
  idx: number;
  name: string;
  state: 'todo' | 'in_progress' | 'done' | 'abandoned';
}

/** Agent 主动提问（Human-in-the-Loop）交互定义 */
export interface UserInteraction {
  toolUseId: string;
  question: string;
  ui_type: string;
  options?: string[];
  fields?: Array<{
    name: string;
    label: string;
    type: string;
    placeholder?: string;
    required?: boolean;
    options?: string[];
    min?: number;
    max?: number;
    step?: number;
  }>;
  default_value?: any;
  allow_other?: boolean;
}

/** 单条结果条目（商品、政策、知识等通用结构，字段按品类可选） */
export interface ResultItem {
  productId?: string;
  name?: string;
  title?: string;
  category?: string;
  brand?: string;
  price?: string;
  originalPrice?: string;
  tags?: string;
  rating?: string;
  sales?: number;
  imageEmoji?: string;
  imageUrl?: string;
  jumpUrl?: string;
  detailUrl?: string;
  productUrl?: string;
  [key: string]: unknown;
}

/** 智能体返回的可视化结果集，type 为品类标识（product / unknown 等） */
export interface AgentResultData {
  type: string;
  items: ResultItem[];
}

/** 下单成功结果，用于渲染带“立即支付”按钮的支付卡片 */
export interface OrderResult {
  type: 'order_result';
  bizType: 'goods' | 'service' | 'unknown';
  bizTypeLabel: string;
  orderId: string;
  title?: string;
  amount?: string | null;
  currency?: string | null;
  payUrl?: string | null;
  statusLabel?: string;
}

/** 方案详情 HTML，用于在 iframe 中渲染完整方案页面 */
export interface PlanHtmlData {
  type: 'plan_html';
  html: string;
  title?: string;
}
