import type { AgentResultData, ResultItem } from '../types';

interface Props {
  data: AgentResultData;
}

const TYPE_LABELS: Record<string, string> = {
  product: '🛍️ 商品推荐',
  unknown: '🔍 查询结果',
};

/** 取商品/结果的展示标题 */
function itemTitle(item: ResultItem): string {
  return String(item.name || item.title || '');
}

/** 取详情跳转链接（兼容多种字段命名） */
function itemLink(item: ResultItem): string | undefined {
  const raw = item.jumpUrl || item.detailUrl || item.productUrl;
  return typeof raw === 'string' && raw.length > 0 ? raw : undefined;
}

// ── 商品卡片 ─────────────────────────────────────────────────────────────
function ProductCard({ item }: { item: ResultItem }) {
  const title = itemTitle(item);
  const link = itemLink(item);
  const tags = typeof item.tags === 'string' ? item.tags.split(',').filter(Boolean) : [];

  return (
    <div className="result-item-card product-card">
      <div className="product-card-content">
        <div className="product-image">
          {item.imageUrl ? (
            <img src={String(item.imageUrl)} alt={title} loading="lazy" />
          ) : (
            <span className="product-image-emoji">{item.imageEmoji || '🛍️'}</span>
          )}
        </div>
        <div className="product-info">
          <div className="product-name-row">
            <span className="product-name">{title}</span>
            {item.brand && <span className="product-brand">{item.brand}</span>}
          </div>
          {item.category && <div className="product-category">{item.category}</div>}
          {tags.length > 0 && (
            <div className="product-tags">
              {tags.slice(0, 4).map((tag) => (
                <span key={tag} className="product-tag">{tag.trim()}</span>
              ))}
            </div>
          )}
          <div className="product-meta-row">
            {item.rating && <span className="product-rating">⭐ {item.rating}</span>}
            {typeof item.sales === 'number' && (
              <span className="product-sales">已售 {item.sales}</span>
            )}
          </div>
        </div>
      </div>
      <div className="product-card-footer">
        <span className="result-price">
          {item.price ? `¥${item.price}` : '价格待询'}
          {item.originalPrice && (
            <span className="price-original">¥{item.originalPrice}</span>
          )}
        </span>
        {link && (
          <a href={link} target="_blank" rel="noopener noreferrer" className="result-link">
            查看详情
          </a>
        )}
      </div>
    </div>
  );
}

// ── 通用兜底卡片：字段不匹配已知结构时按 key-value 平铺展示 ─────────────
function GenericItemCard({ item }: { item: ResultItem }) {
  const entries = Object.entries(item).filter(
    ([, v]) => v !== null && v !== undefined && String(v).length > 0,
  );
  const link = itemLink(item);
  const title = itemTitle(item);

  return (
    <div className="result-item-card generic-card">
      {title && <div className="generic-card-title">{title}</div>}
      <div className="generic-card-body">
        {entries.slice(0, 8).map(([key, value]) => (
          <div key={key} className="generic-card-row">
            <span className="generic-card-key">{key}:</span>
            <span className="generic-card-value">{String(value)}</span>
          </div>
        ))}
      </div>
      {link && (
        <div className="generic-card-footer">
          <a href={link} target="_blank" rel="noopener noreferrer" className="result-link">
            查看详情
          </a>
        </div>
      )}
    </div>
  );
}

// ── 主组件 ───────────────────────────────────────────────────────────────
export default function AgentResultCard({ data }: Props) {
  const label = TYPE_LABELS[data.type] || '🔍 查询结果';
  const isProduct = data.type === 'product';

  return (
    <div className="result-card">
      <div className="result-card-header">
        <span className="result-card-title">{label}</span>
        <span className="result-card-count">{data.items.length} 条结果</span>
      </div>
      <div className="result-card-list">
        {data.items.map((item, i) =>
          isProduct ? (
            <ProductCard key={i} item={item} />
          ) : (
            <GenericItemCard key={i} item={item} />
          ),
        )}
      </div>
    </div>
  );
}
