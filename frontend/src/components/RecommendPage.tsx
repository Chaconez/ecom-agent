import { useCallback, useEffect, useState } from 'react';
import { fetchRecommendations, type RecommendItem } from '../api/recommend';
import { useAuthStore } from '../store/authStore';
import { useUiStore } from '../store/uiStore';

function formatSales(sales: number | null): string {
  if (!sales) return '暂无销量';
  if (sales >= 10000) return `${(sales / 10000).toFixed(1)} 万人付款`;
  return `${sales} 人付款`;
}

function ProductCard({ item }: { item: RecommendItem }) {
  const tags = item.tags
    ? item.tags.split(',').filter(Boolean).slice(0, 3)
    : [];
  const discount =
    item.price && item.originalPrice && Number(item.originalPrice) > Number(item.price)
      ? (Number(item.price) / Number(item.originalPrice) * 10).toFixed(1)
      : null;

  return (
    <div className="product-card">
      <div className="product-img">
        <span className="product-img-emoji">{item.imageEmoji || '🛒'}</span>
        <span className="product-category">{item.category}</span>
        {discount && <span className="product-discount">{discount} 折</span>}
      </div>
      <div className="product-info">
        <div className="product-name" title={item.name}>{item.name}</div>
        {item.brand && <div className="product-brand">{item.brand}</div>}
        <div className="product-price-row">
          <span className="product-price-symbol">¥</span>
          <span className="product-price">{item.price}</span>
          {item.originalPrice && (
            <span className="product-original-price">¥{item.originalPrice}</span>
          )}
        </div>
        <div className="product-meta">
          {item.rating && <span>⭐ {item.rating}</span>}
          <span>{formatSales(item.sales)}</span>
        </div>
        {tags.length > 0 && (
          <div className="product-tags">
            {tags.map((t) => (
              <span className="product-tag" key={t}>{t}</span>
            ))}
          </div>
        )}
        <div className="product-reason">
          <span className="product-reason-icon">💡</span>
          <span className="product-reason-text">{item.reason}</span>
        </div>
      </div>
    </div>
  );
}

export default function RecommendPage() {
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const setView = useUiStore((s) => s.setView);

  const [items, setItems] = useState<RecommendItem[]>([]);
  const [hasPreferences, setHasPreferences] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await fetchRecommendations(8);
      setItems(data.items);
      setHasPreferences(data.hasPreferences);
    } catch (err: any) {
      if (err?.message === 'UNAUTHORIZED') {
        clearAuth();
        return;
      }
      setError('加载推荐失败，请稍后重试');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [clearAuth]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <main className="admin-page recommend-page">
      <div className="admin-header">
        <div>
          <h1 className="admin-title">⭐ 为你推荐</h1>
          <p className="admin-subtitle">基于你的偏好与购物习惯，智能挑选好物</p>
        </div>
        <button className="admin-refresh" onClick={load} disabled={loading}>
          {loading ? '推荐中...' : '换一批'}
        </button>
      </div>

      {!hasPreferences && !loading && (
        <div className="recommend-banner">
          <span className="recommend-banner-icon">🎯</span>
          <span className="recommend-banner-text">
            还没有偏好设置，先去勾选常购品类，推荐会更懂你
          </span>
          <button className="recommend-banner-btn" onClick={() => setView('preference')}>
            去设置偏好
          </button>
        </div>
      )}

      {error && <div className="admin-error">{error}</div>}

      {loading ? (
        <div className="admin-empty">正在为你挑选好物...</div>
      ) : items.length === 0 ? (
        <div className="admin-empty">暂无推荐商品，稍后再试试</div>
      ) : (
        <div className="product-grid">
          {items.map((item) => (
            <ProductCard key={item.productId} item={item} />
          ))}
        </div>
      )}
    </main>
  );
}
