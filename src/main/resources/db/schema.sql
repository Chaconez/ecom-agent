-- 电商智能客服数据库初始化脚本
-- 请在 MySQL 中先执行 CREATE DATABASE ecom_cs DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS agentscope_session (
                                                   session_id VARCHAR(255) NOT NULL,
    state_key VARCHAR(255) NOT NULL,
    item_index INT NOT NULL DEFAULT 0,
    state_data LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, state_key, item_index)
    ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS `travel_order` (
    `order_id`       VARCHAR(64)  NOT NULL COMMENT '差旅单ID',
    `user_id`        VARCHAR(64)  NOT NULL COMMENT '申请人ID',
    `destination`    VARCHAR(256) DEFAULT NULL COMMENT '目的地',
    `departure_city` VARCHAR(128) DEFAULT NULL COMMENT '出发城市',
    `departure_date` VARCHAR(32)  DEFAULT NULL COMMENT '出发日期',
    `return_date`    VARCHAR(32)  DEFAULT NULL COMMENT '返回日期',
    `purpose`        VARCHAR(512) DEFAULT NULL COMMENT '出差事由',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/SUBMITTED/APPROVED/REJECTED/COMPLETED/CANCELLED',
    `approval_id`    VARCHAR(64)  DEFAULT NULL COMMENT '关联审批单ID',
    `plan_html_url`  VARCHAR(512) DEFAULT NULL COMMENT '行程方案HTML的MinIO对象key',
    `created_at`     DATETIME     DEFAULT NULL COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT NULL COMMENT '最后修改时间',
    PRIMARY KEY (`order_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '差旅申请单';


CREATE TABLE IF NOT EXISTS `approval_record` (
    `process_instance_id` VARCHAR(64)   NOT NULL COMMENT '审批流程实例ID',
    `user_id`             VARCHAR(64)   NOT NULL COMMENT '申请人ID',
    `title`               VARCHAR(256)  DEFAULT NULL COMMENT '审批标题',
    `status`              VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED/CANCELLED',
    `approval_form`       TEXT          DEFAULT NULL COMMENT '审批表单JSON',
    `remark`              VARCHAR(512)  DEFAULT NULL COMMENT '备注',
    `submit_time`         DATETIME      DEFAULT NULL COMMENT '提交时间',
    `update_time`         DATETIME      DEFAULT NULL COMMENT '最后更新时间',
    `order_id`            VARCHAR(64)   DEFAULT NULL COMMENT '关联差旅单ID',
    PRIMARY KEY (`process_instance_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_submit` (`order_id`, `submit_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '差旅审批记录';


CREATE TABLE IF NOT EXISTS `user_profile` (
    `user_id`       VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `base_city`     VARCHAR(128) DEFAULT NULL COMMENT '常驻城市',
    `level`         VARCHAR(16)  DEFAULT NULL COMMENT '职级，如 P5/P6/P7/P8',
    `name_pinyin`   VARCHAR(128) DEFAULT NULL COMMENT '姓名拼音（大写，姓在前名在后，空格分隔），如 ZHANG SAN，用于酒店预订',
    `email`         VARCHAR(128) DEFAULT NULL COMMENT '用户邮箱，用于酒店预订下单联系人',
    `chinese_name`  VARCHAR(64)  DEFAULT NULL COMMENT '中文姓名，如 张三，用于机票预订乘客信息',
    `id_type`       INT          DEFAULT NULL COMMENT '证件类型（0-身份证 1-护照 2-其他 3-回乡证 4-军官证 5-警官证 6-港澳通行证 7-台胞证 8-台湾通行证 9-外国人永久居留身份证）',
    `id_number`     VARCHAR(64)  DEFAULT NULL COMMENT '证件号码（身份证/护照等），用于机票预订',
    `phone`         VARCHAR(32)  DEFAULT NULL COMMENT '手机号，用于机票预订联系人',
    `gender`        VARCHAR(4)   DEFAULT NULL COMMENT '性别：M-男，F-女',
    PRIMARY KEY (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户档案';


CREATE TABLE IF NOT EXISTS `user_account` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`      VARCHAR(64)  NOT NULL COMMENT '关联 user_profile.user_id',
    `username`     VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`     VARCHAR(128) NOT NULL COMMENT '登录密码（生产环境应使用 BCrypt 加密）',
    `real_name`    VARCHAR(64)  DEFAULT NULL COMMENT '用户真实姓名',
    `role`         VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER 普通用户 / ADMIN 管理员',
    `created_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id`  (`user_id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户登录账号表';

-- ============================================================
-- 差旅政策配置（城市等级已改由 application.yml 配置，此处仅管理政策规则）
-- ============================================================

CREATE TABLE IF NOT EXISTS `travel_policy_rule` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `level_min`             INT         NOT NULL COMMENT '职级区间下限（数字）',
    `level_max`             INT         NOT NULL COMMENT '职级区间上限（99表示无上限）',
    `city_tier`             VARCHAR(16) NOT NULL COMMENT '城市等级：一线/新一线/其他',
    `flight_class`          VARCHAR(32) DEFAULT NULL COMMENT '允许的最高机票舱位',
    `train_seat_class`      VARCHAR(16) DEFAULT NULL COMMENT '允许的最高高铁座位',
    `hotel_limit`           DOUBLE      NOT NULL DEFAULT 0 COMMENT '酒店每晚上限（元）',
    `hotel_star_limit`      INT         NOT NULL DEFAULT 4 COMMENT '酒店星级上限',
    `daily_meal_limit`      DOUBLE      NOT NULL DEFAULT 0 COMMENT '餐补每日上限（元）',
    `daily_transport_limit` DOUBLE      NOT NULL DEFAULT 0 COMMENT '交通补贴每日上限（元）',
    `approval_threshold`    DOUBLE      NOT NULL DEFAULT 0 COMMENT '审批金额阈値（超过此金额需审批）',
    `advance_booking_days`  INT         NOT NULL DEFAULT 3 COMMENT '提前预订最少天数',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_city` (`level_min`, `level_max`, `city_tier`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '差旅政策规则表';


-- ============================================================
-- 对话与消息历史（前端历史会话展示）
-- ============================================================

CREATE TABLE IF NOT EXISTS `chat_conversation` (
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID（同前端 sessionId）',
    `user_id`         VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `title`           VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `mode`            VARCHAR(16)  NOT NULL DEFAULT 'AI' COMMENT '会话模式：AI 智能助手 / HUMAN 人工接管',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (`conversation_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_updated_at` (`updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话会话';

CREATE TABLE IF NOT EXISTS `chat_message` (
    `message_id`      VARCHAR(64)  NOT NULL COMMENT '消息ID',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `role`            VARCHAR(32)  NOT NULL COMMENT '角色：user/agent/system',
    `content`         TEXT         DEFAULT NULL COMMENT '消息内容',
    `agent_name`      VARCHAR(128) DEFAULT NULL COMMENT 'Agent名称（role=agent 时）',
    `extra`           JSON         DEFAULT NULL COMMENT '扩展信息（进度快照/推荐问题等）',
    `feedback`        VARCHAR(16)  DEFAULT NULL COMMENT '用户反馈：LIKE 点赞 / DISLIKE 点踩 / NULL 未反馈',
    `feedback_at`     DATETIME     DEFAULT NULL COMMENT '反馈时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (`message_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话消息';

-- ============================================================
-- 用户 API Key 存储（加密存储，支持多服务提供商）
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_api_key` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`      VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `provider`     VARCHAR(64)  NOT NULL COMMENT '服务提供商，如 flight-manager',
    `api_key_enc`  VARCHAR(512) NOT NULL COMMENT 'AES 加密后的 API Key（Base64 编码）',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_provider` (`user_id`, `provider`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户第三方 API Key（加密存储）';

-- ============================================================
-- Agent 预订记录（机票 / 酒店 / 火车票等，兼容多预订平台）
-- ============================================================

CREATE TABLE IF NOT EXISTS `booking_record` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `booking_id`        VARCHAR(64)   NOT NULL COMMENT '内部预订单号（系统生成，全局唯一）',
    `user_id`           VARCHAR(64)   NOT NULL COMMENT '用户ID',
    `conversation_id`   VARCHAR(64)   DEFAULT NULL COMMENT '关联会话ID（chat_conversation.conversation_id）',
    `travel_order_id`    VARCHAR(64)   DEFAULT NULL COMMENT '关联差旅单ID（travel_order.order_id，可为空）',
    `biz_type`          VARCHAR(16)   NOT NULL COMMENT '预订业务类型: FLIGHT/HOTEL/TRAIN/TICKET/CRUISE/VACATION',
    `platform`          VARCHAR(32)   NOT NULL COMMENT '预订平台: tuniu / rolling-go-hotel / flight-manager 等',
    `external_order_no` VARCHAR(128)  DEFAULT NULL COMMENT '外部平台主订单号',
    `status`            VARCHAR(32)   NOT NULL DEFAULT 'CREATED' COMMENT '统一预订状态: CREATED/PENDING_PAYMENT/PAID/CONFIRMED/COMPLETED/CANCELLED/REFUNDED/FAILED',
    `external_status`   VARCHAR(64)   DEFAULT NULL COMMENT '外部平台原始状态码/文案（保留平台原值）',
    `payment_status`    VARCHAR(32)   DEFAULT NULL COMMENT '支付状态: UNPAID/PAID/REFUNDED',
    `title`             VARCHAR(256)  DEFAULT NULL COMMENT '预订标题（如“北京→上海 MU5101”或酒店名）',
    `total_amount`      DECIMAL(12,2) DEFAULT NULL COMMENT '订单总金额（元）',
    `currency`          VARCHAR(8)    DEFAULT 'CNY' COMMENT '币种',
    `contact_name`      VARCHAR(64)   DEFAULT NULL COMMENT '联系人姓名',
    `contact_phone`     VARCHAR(32)   DEFAULT NULL COMMENT '联系人手机号',
    `start_time`        DATETIME      DEFAULT NULL COMMENT '服务开始时间（出发/入住时间）',
    `end_time`          DATETIME      DEFAULT NULL COMMENT '服务结束时间（到达/离店时间）',
    `detail`            JSON          DEFAULT NULL COMMENT '平台原始/明细信息JSON（航班号、房型、乘客、行程等）',
    `remark`            VARCHAR(512)  DEFAULT NULL COMMENT '备注',
    `booked_at`         DATETIME      DEFAULT NULL COMMENT '下单时间',
    `created_at`        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_booking_id` (`booking_id`),
    UNIQUE KEY `uk_platform_order` (`platform`, `biz_type`, `external_order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_biz` (`user_id`, `biz_type`),
    KEY `idx_travel_order_id` (`travel_order_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent预订记录（机票/酒店/火车票等，兼容多平台）';


-- ============================================================
-- 测试数据（本地开发/演示用，生产环境按需清空）
-- ============================================================

-- 1. 用户档案
INSERT IGNORE INTO `user_profile` (`user_id`, `base_city`, `level`, `name_pinyin`, `email`, `chinese_name`, `id_type`, `phone`, `gender`) VALUES
    ('u_001', '北京', 'P7', 'ZHANG SAN',   'zhangsan@example.com', '张三', 0, '13800000001', 'M'),
    ('u001',  '上海', 'P7', 'LI SI',       'lisi@example.com',     '李四', 0, '13800000002', 'M'),
    ('u002',  '北京', 'P6', 'WANG WU',     'wangwu@example.com',   '王五', 0, '13800000003', 'M'),
    ('u003',  '成都', 'P5', 'ZHAO LIU',    'zhaoliu@example.com',  '赵六', 0, '13800000004', 'F'),
    ('u004',  '深圳', 'P8', 'CHEN QI',     'chenqi@example.com',   '陈七', 0, '13800000005', 'M');

-- 2. 登录账号（密码明文 123456，仅限本地开发；生产环境请替换为 BCrypt 哈希）
INSERT IGNORE INTO `user_account` (`user_id`, `username`, `password`, `real_name`, `role`) VALUES
    ('u_001', 'admin',   '123456', '系统管理员', 'ADMIN'),
    ('u001',  'alice',   '123456', '张三',     'USER'),
    ('u002',  'bob',     '123456', '李四',     'USER'),
    ('u003',  'charlie', '123456', '王五',     'USER'),
    ('u004',  'david',   '123456', '赵六',     'USER'),
    ('u_test', 'test',   '123456', '测试账号',  'USER');

-- 3. 差旅政策规则（4 个职级区间 × 3 个城市等级 = 12 条）
-- 列顺序：level_min, level_max, city_tier, flight_class, train_seat_class,
--         hotel_limit, hotel_star_limit, daily_meal_limit, daily_transport_limit, approval_threshold, advance_booking_days
INSERT IGNORE INTO `travel_policy_rule`
    (`level_min`,`level_max`,`city_tier`,`flight_class`,`train_seat_class`,`hotel_limit`,`hotel_star_limit`,`daily_meal_limit`,`daily_transport_limit`,`approval_threshold`,`advance_booking_days`)
VALUES
    -- P8+：商务舱 / 一等座
    (8, 99, '一线',   '商务舱',        '一等座', 700, 5, 200, 300, 8000, 3),
    (8, 99, '新一线', '商务舱',        '一等座', 550, 5, 200, 300, 8000, 3),
    (8, 99, '其他',   '商务舱',        '一等座', 450, 5, 200, 300, 8000, 3),
    -- P7：经济舱/商务舱 / 一等座
    (7, 7,  '一线',   '经济舱/商务舱', '一等座', 600, 5, 200, 300, 8000, 3),
    (7, 7,  '新一线', '经济舱/商务舱', '一等座', 500, 5, 200, 300, 8000, 3),
    (7, 7,  '其他',   '经济舱/商务舱', '一等座', 400, 5, 200, 300, 8000, 3),
    -- P6：经济舱 / 一等座
    (6, 6,  '一线',   '经济舱',        '一等座', 500, 4, 150, 200, 5000, 3),
    (6, 6,  '新一线', '经济舱',        '一等座', 400, 4, 150, 200, 5000, 3),
    (6, 6,  '其他',   '经济舱',        '一等座', 350, 4, 150, 200, 5000, 3),
    -- P5-：经济舱 / 二等座
    (1, 5,  '一线',   '经济舱',        '二等座', 400, 4, 150, 200, 5000, 3),
    (1, 5,  '新一线', '经济舱',        '二等座', 350, 4, 150, 200, 5000, 3),
    (1, 5,  '其他',   '经济舱',        '二等座', 300, 4, 150, 200, 5000, 3);

-- 4. 差旅申请单示例（覆盖 DRAFT / SUBMITTED / APPROVED 三种典型状态）
INSERT IGNORE INTO `travel_order`
    (`order_id`, `user_id`, `destination`, `departure_city`, `departure_date`, `return_date`, `purpose`, `status`, `approval_id`, `created_at`, `updated_at`)
VALUES
    ('to_20260701_001', 'u001', '上海', '北京', '2026-07-10', '2026-07-12', '客户拜访',   'APPROVED',  'ap_20260701_001', '2026-07-01 10:00:00', '2026-07-02 09:00:00'),
    ('to_20260710_002', 'u001', '深圳', '北京', '2026-07-20', '2026-07-22', '产品交流会', 'SUBMITTED', 'ap_20260710_002', '2026-07-10 14:30:00', '2026-07-10 14:30:00'),
    ('to_20260715_003', 'u002', '成都', '上海', '2026-08-01', '2026-08-03', '业务培训',   'DRAFT',     NULL,              '2026-07-15 09:00:00', '2026-07-15 09:00:00');

-- 5. 审批记录示例（关联上面的差旅单）
INSERT IGNORE INTO `approval_record`
    (`process_instance_id`, `user_id`, `title`, `status`, `approval_form`, `remark`, `submit_time`, `update_time`, `order_id`)
VALUES
    ('ap_20260701_001', 'u001', '张三-北京→上海-2026/07/10~07/12', 'APPROVED', '{"destination":"上海","budget":3000}', '预算合理，同意', '2026-07-01 10:05:00', '2026-07-02 09:00:00', 'to_20260701_001'),
    ('ap_20260710_002', 'u001', '张三-北京→深圳-2026/07/20~07/22', 'PENDING',  '{"destination":"深圳","budget":4500}', NULL,             '2026-07-10 14:35:00', '2026-07-10 14:35:00', 'to_20260710_002');

-- 6. 外部预订记录示例（已通过 tuniu-cli / rolling-go-hotel 完成的机票 + 酒店）
INSERT IGNORE INTO `booking_record`
    (`booking_id`, `user_id`, `conversation_id`, `travel_order_id`, `biz_type`, `platform`, `external_order_no`, `status`, `payment_status`, `title`, `total_amount`, `currency`, `contact_name`, `contact_phone`, `start_time`, `end_time`, `remark`, `booked_at`)
VALUES
    ('bk_20260702_001', 'u001', NULL, 'to_20260701_001', 'FLIGHT', 'tuniu',             'TN2026070200001',  'CONFIRMED', 'PAID', '北京→上海 MU5101 07/10 09:00',    1280.00, 'CNY', '张三', '13800000002', '2026-07-10 09:00:00', '2026-07-10 11:20:00', '示例数据', '2026-07-02 10:30:00'),
    ('bk_20260702_002', 'u001', NULL, 'to_20260701_001', 'HOTEL',  'rolling-go-hotel',  'RGH20260702HTL88', 'CONFIRMED', 'PAID', '上海静安希尔顿酒店-高级大床房',    1580.00, 'CNY', '张三', '13800000002', '2026-07-10 15:00:00', '2026-07-12 12:00:00', '示例数据', '2026-07-02 10:45:00');-- ============================================================
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
CREATE TABLE IF NOT EXISTS `user_preference` (
    `user_id`      VARCHAR(64) NOT NULL COMMENT '用户ID',
    `pref_key`     VARCHAR(64) NOT NULL COMMENT '偏好字段 key',
    `pref_value`   TEXT        NOT NULL COMMENT '偏好值（字符串或 JSON 数组）',
    `updated_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `pref_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户偏好持久化';
-- ============================================================
-- 电商智能客服：test 账号（user_id=u_test）演示数据
-- 让「我的订单」「客服工作台」「聊天查订单/工单」对演示账号可用
-- ============================================================

-- 1. 用户档案（供 query_user_base_location 查询常用城市）
INSERT IGNORE INTO `user_profile` (`user_id`, `base_city`, `level`, `chinese_name`, `phone`) VALUES
('u_test', '上海', 'NORMAL', '测试账号', '13800000007');

-- 2. 服务工单主表（travel_order：工单语义化后的字段映射）
--    destination=问题对象（商品）  departure_city=联系城市
--    departure_date=申请日期  return_date=期望处理截止  purpose=问题描述
INSERT IGNORE INTO `travel_order`
    (`order_id`, `user_id`, `destination`, `departure_city`, `departure_date`, `return_date`, `purpose`, `status`, `approval_id`, `created_at`, `updated_at`)
VALUES
    ('to_20261018_701', 'u_test', '主动降噪无线蓝牙耳机 Pro', '上海', '2026-10-18', '2026-10-25', '右耳偶发断连，申请换货', 'SUBMITTED', 'ap_20261018_701', '2026-10-18 10:00:00', '2026-10-18 10:05:00'),
    ('to_20261012_702', 'u_test', '激光导航扫地机器人', '上海', '2026-10-12', '2026-10-20', '清扫路径规划异常，申请维修', 'APPROVED', 'ap_20261012_702', '2026-10-12 14:30:00', '2026-10-12 14:35:00'),
    ('to_20261005_703', 'u_test', 'SPF50+ 清爽防晒霜', '上海', '2026-10-05', '2026-10-08', '商品破损，申请退款', 'CANCELLED', 'ap_20261005_703', '2026-10-05 09:15:00', '2026-10-05 09:20:00');

-- 3. 工单处理记录（approval_record）
INSERT IGNORE INTO `approval_record`
    (`process_instance_id`, `user_id`, `title`, `status`, `approval_form`, `remark`, `submit_time`, `update_time`, `order_id`)
VALUES
    ('ap_20261018_701', 'u_test', '测试账号-耳机换货-2026/10/18', 'PENDING',
     '{"type":"after_sale","product":"主动降噪无线蓝牙耳机 Pro","orderNo":"EC20261018001","issue":"右耳偶发断连","expect":"换货"}',
     NULL, '2026-10-18 10:05:00', '2026-10-18 10:05:00', 'to_20261018_701'),
    ('ap_20261012_702', 'u_test', '测试账号-扫地机维修-2026/10/12', 'APPROVED',
     '{"type":"repair","product":"激光导航扫地机器人","orderNo":"EC20261012005","issue":"清扫路径规划异常","expect":"维修"}',
     '已安排上门取件检测', '2026-10-12 14:35:00', '2026-10-13 09:00:00', 'to_20261012_702'),
    ('ap_20261005_703', 'u_test', '测试账号-防晒霜退款-2026/10/05', 'REJECTED',
     '{"type":"refund","product":"SPF50+ 清爽防晒霜","orderNo":"EC20261005004","issue":"商品破损","expect":"退款"}',
     '已超出售后期，建议联系快递理赔', '2026-10-05 09:20:00', '2026-10-06 11:00:00', 'to_20261005_703');

-- 4. 商品订单（booking_record，biz_type=GOODS）
INSERT IGNORE INTO `booking_record`
    (`booking_id`, `user_id`, `conversation_id`, `travel_order_id`, `biz_type`, `platform`, `external_order_no`, `status`, `external_status`, `payment_status`, `title`, `total_amount`, `currency`, `contact_name`, `contact_phone`, `start_time`, `end_time`, `detail`, `remark`, `booked_at`)
VALUES
    ('bk_20261018_701', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261018001', 'CONFIRMED', '已发货', 'PAID',
     '主动降噪无线蓝牙耳机 Pro × 1', 799.00, 'CNY', '测试账号', '13800000007',
     '2026-10-18 10:00:00', NULL,
     '{"product_id":"p_001","quantity":1,"address":"上海市浦东新区演示路 1 号","logistics":{"carrier":"顺丰","trackingNo":"SF1234567890","status":"运输中"}}',
     '示例数据', '2026-10-18 10:00:00'),
    ('bk_20261015_702', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261015002', 'PENDING_PAYMENT', '待支付', 'UNPAID',
     '缓震专业跑步鞋 × 1', 459.00, 'CNY', '测试账号', '13800000007',
     '2026-10-15 20:30:00', NULL,
     '{"product_id":"p_009","quantity":1,"address":"上海市浦东新区演示路 1 号"}',
     '示例数据', '2026-10-15 20:30:00'),
    ('bk_20261010_703', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261010003', 'COMPLETED', '已完成', 'PAID',
     '三合一防风冲锋衣 × 1', 659.00, 'CNY', '测试账号', '13800000007',
     '2026-10-10 09:00:00', '2026-10-13 16:00:00',
     '{"product_id":"p_008","quantity":1,"address":"上海市浦东新区演示路 1 号"}',
     '示例数据', '2026-10-10 09:00:00'),
    ('bk_20261005_704', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261005004', 'CANCELLED', '已取消', NULL,
     'SPF50+ 清爽防晒霜 × 2', 258.00, 'CNY', '测试账号', '13800000007',
     '2026-10-05 15:00:00', NULL,
     '{"product_id":"p_012","quantity":2,"address":"上海市浦东新区演示路 1 号"}',
     '示例数据', '2026-10-05 15:00:00');

-- ============================================================
-- 电商智能客服：意图种子语料已外置至 intent-seed.yml（L2 向量库）
-- ============================================================

-- ============================================================
-- 管理端（B 端）：操作审计日志（2026-10-21 新增）
-- 记录管理端所有写操作（接管/释放/人工发言/审批决策），供审计页查询
-- ============================================================
CREATE TABLE IF NOT EXISTS `admin_audit_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `admin_user_id` VARCHAR(64)  NOT NULL COMMENT '操作人（sa-token loginId）',
    `action`        VARCHAR(64)  NOT NULL COMMENT '动作：takeover/release/human_message/approval_decide',
    `target_type`   VARCHAR(32)  NOT NULL COMMENT '对象类型：conversation/approval',
    `target_id`     VARCHAR(64)  DEFAULT NULL COMMENT '对象ID（会话ID/工单ID）',
    `detail`        JSON         DEFAULT NULL COMMENT '操作详情快照',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_admin_user` (`admin_user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理端操作审计日志';

-- ============================================================
-- 存量库迁移（2026-10-21）：chat_conversation 增加 mode 字段
-- 新库已含在 CREATE TABLE 中；老库执行下面两句（幂等，重复执行报错可忽略）
-- ============================================================
-- ALTER TABLE `chat_conversation`
--     ADD COLUMN `mode` VARCHAR(16) NOT NULL DEFAULT 'AI'
--     COMMENT '会话模式：AI 智能助手 / HUMAN 人工接管' AFTER `title`;
