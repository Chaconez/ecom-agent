-- ============================================================
-- 电商智能客服：个性化推荐模块（2026-10-20 新增）
-- 1. ecom_product      商品库（mock 数据，P3 替换为真实商品中心）
-- 2. user_preference   用户偏好持久化（修复长期记忆读回延迟问题，DB 作为权威源）
-- ============================================================

-- ------------------------------------------------ 商品表 --------------
CREATE TABLE IF NOT EXISTS `ecom_product` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `product_id`     VARCHAR(64)  NOT NULL COMMENT '商品编号',
    `name`           VARCHAR(256) NOT NULL COMMENT '商品名称',
    `category`       VARCHAR(32)  NOT NULL COMMENT '品类（与偏好选项对齐）',
    `brand`          VARCHAR(64)  DEFAULT NULL COMMENT '品牌',
    `price`          DECIMAL(10,2) NOT NULL COMMENT '售价',
    `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原价（划线价）',
    `tags`           VARCHAR(256) DEFAULT NULL COMMENT '标签，逗号分隔',
    `rating`         DECIMAL(2,1) DEFAULT NULL COMMENT '评分 0-5',
    `sales`          INT          DEFAULT 0 COMMENT '销量',
    `image_emoji`    VARCHAR(8)   DEFAULT NULL COMMENT '商品图标（emoji，避免外链图）',
    `description`    VARCHAR(512) DEFAULT NULL COMMENT '一句话卖点',
    `created_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_id` (`product_id`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品库（mock）';

INSERT IGNORE INTO `ecom_product`
(`product_id`, `name`, `category`, `brand`, `price`, `original_price`, `tags`, `rating`, `sales`, `image_emoji`, `description`) VALUES
('p_001', '主动降噪无线蓝牙耳机 Pro', '数码电子', 'SoundCore', 799.00, 1099.00, '降噪,无线,蓝牙,长续航', 4.8, 15230, '🎧', '42dB 深度降噪，单次续航 8 小时'),
('p_002', '影像旗舰智能手机 5G', '数码电子', 'NovaTech', 3299.00, 3699.00, '5G,影像,快充,大屏', 4.7, 8920, '📱', '一亿像素主摄，120W 快充'),
('p_003', '全天候健康监测智能手表', '数码电子', 'FitLife', 1299.00, 1599.00, '健康监测,运动,防水,NFC', 4.6, 6410, '⌚', '血氧心率睡眠全监测，14 天续航'),
('p_004', '轻薄护眼平板电脑 11 英寸', '数码电子', 'NovaTech', 2199.00, 2499.00, '护眼屏,轻薄,长续航,手写笔', 4.5, 4320, '📟', '2.8K 护眼屏，支持手写笔'),
('p_005', '家用空气净化器 CADR 600', '家用电器', 'AirPure', 1899.00, 2299.00, '除甲醛,静音,智能,大空间', 4.7, 5310, '🌀', '除甲醛率 99%，睡眠模式 22dB'),
('p_006', '激光导航扫地机器人', '家用电器', 'CleanBot', 2699.00, 3199.00, '扫拖一体,激光导航,自动集尘', 4.8, 9870, '🤖', '扫拖洗烘一体，60 天免倒尘'),
('p_007', 'IH 电磁加热智能电饭煲 4L', '家用电器', 'CookWell', 499.00, 699.00, 'IH加热,智能,大容量', 4.6, 12300, '🍚', '柴火饭口感，24 小时预约'),
('p_008', '三合一防风冲锋衣', '服饰鞋包', 'OutdoorPro', 659.00, 899.00, '防风,防水,透气,春秋', 4.5, 7620, '🧥', '2 层压胶面料，暴雨级防水'),
('p_009', '缓震专业跑步鞋', '服饰鞋包', 'StrideX', 459.00, 599.00, '缓震,透气,轻量,马拉松', 4.7, 15600, '👟', '超临界发泡中底，单只 210g'),
('p_010', '防泼水通勤双肩包 25L', '服饰鞋包', 'UrbanCarry', 239.00, 329.00, '大容量,防泼水,电脑仓,通勤', 4.4, 9310, '🎒', '独立电脑仓，通过 1.6m 跌落测试'),
('p_011', '烟酰胺美白精华液 30ml', '美妆护肤', 'GlowLab', 329.00, 429.00, '美白,烟酰胺,保湿,淡斑', 4.6, 21400, '🧴', '5% 烟酰胺，28 天提亮肤色'),
('p_012', 'SPF50+ 清爽防晒霜', '美妆护肤', 'GlowLab', 129.00, 169.00, '防晒,清爽,不油腻,敏感肌', 4.8, 18700, '☀️', '物化结合，游泳场景可用'),
('p_013', '氨基酸温和洁面乳', '美妆护肤', 'PureSkin', 79.00, 99.00, '温和,氨基酸,敏感肌,控油', 4.5, 25300, '🧼', '弱酸性配方，洗后不紧绷'),
('p_014', '每日坚果混合礼盒 750g', '食品生鲜', 'NutriDay', 128.00, 168.00, '坚果,独立包装,礼盒,无添加', 4.7, 34200, '🥜', '6 种坚果 3 种果干，30 天装'),
('p_015', '有机高钙纯牛奶 250ml×24', '食品生鲜', 'GreenFarm', 89.00, 119.00, '有机,高钙,原生蛋白,整箱', 4.6, 28900, '🥛', '3.6g/100ml 优质乳蛋白'),
('p_016', '中度烘焙阿拉比卡咖啡豆 500g', '食品生鲜', 'BeanHouse', 69.00, 89.00, '咖啡豆,中烘,坚果香,手冲', 4.5, 16400, '☕', 'SCA 80+ 评分，7 天内新鲜烘焙'),
('p_017', '超薄透气纸尿裤 XL 码', '母婴亲子', 'BabySoft', 139.00, 179.00, '超薄,透气,不过敏,夜用', 4.8, 19800, '🍼', '0.2cm 芯体，12 小时干爽'),
('p_018', '婴儿辅食蒸煮搅拌一体机', '母婴亲子', 'BabySoft', 359.00, 459.00, '辅食,蒸煮,搅拌,易清洗', 4.6, 8300, '🥣', '一键蒸搅，微量也能打'),
('p_019', '60 支长绒棉四件套', '家居日用', 'HomeSoft', 399.00, 599.00, '长绒棉,亲肤,静色,床品', 4.7, 11200, '🛏️', '60 支贡缎，A 类婴幼儿标准'),
('p_020', '折叠收纳箱 50L 三个装', '家居日用', 'StoreWell', 79.00, 109.00, '收纳,折叠,大容量,可叠加', 4.4, 21500, '📦', '免安装折叠，承重 30kg'),
('p_021', '香薰蜡烛礼盒 大豆蜡', '家居日用', 'AromaHouse', 159.00, 219.00, '香薰,大豆蜡,礼盒,助眠', 4.5, 7600, '🕯️', '天然大豆蜡，4 种香型'),
('p_022', 'TPE 双面防滑瑜伽垫 8mm', '运动户外', 'YogaZone', 129.00, 179.00, '瑜伽,防滑,加厚,环保', 4.6, 13800, '🧘', 'TPE 环保材质，体位线辅助'),
('p_023', '双人全自动露营帐篷', '运动户外', 'OutdoorPro', 899.00, 1199.00, '露营,全自动,防雨,双人', 4.7, 5400, '⛺', '3 秒自动展开，防暴雨等级'),
('p_024', '负重燃脂专业跳绳', '运动户外', 'StrideX', 49.00, 69.00, '跳绳,燃脂,负重,计数', 4.5, 26400, '🪢', '轴承钢丝绳，APP 计数');

-- -------------------------------------------- 用户偏好持久化表 --------
-- 长期记忆（百炼 LTM）存在索引延迟，DB 作为偏好权威源，读接口优先查库
DROP TABLE IF EXISTS `user_preference`;
CREATE TABLE IF NOT EXISTS `user_preference` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`      VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `pref_key`     VARCHAR(64)  NOT NULL COMMENT '偏好字段 key',
    `pref_value`   TEXT         NOT NULL COMMENT '偏好值（字符串或 JSON 数组）',
    `updated_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_pref` (`user_id`, `pref_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户偏好持久化';
