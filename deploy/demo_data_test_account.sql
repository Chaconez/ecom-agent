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
    (`order_no`, `user_id`, `conversation_id`, `after_sale_ticket_id`, `biz_type`, `platform`, `external_order_no`, `status`, `external_status`, `payment_status`, `title`, `total_amount`, `currency`, `contact_name`, `contact_phone`, `start_time`, `end_time`, `detail`, `remark`, `ordered_at`)
VALUES
    ('ec_20261018_701', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261018001', 'CONFIRMED', '已发货', 'PAID',
     '主动降噪无线蓝牙耳机 Pro × 1', 799.00, 'CNY', '测试账号', '13800000007',
     '2026-10-18 10:00:00', NULL,
     '{"product_id":"p_001","quantity":1,"address":"上海市浦东新区演示路 1 号","logistics":{"carrier":"顺丰","trackingNo":"SF1234567890","status":"运输中"}}',
     '示例数据', '2026-10-18 10:00:00'),
    ('ec_20261015_702', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261015002', 'PENDING_PAYMENT', '待支付', 'UNPAID',
     '缓震专业跑步鞋 × 1', 459.00, 'CNY', '测试账号', '13800000007',
     '2026-10-15 20:30:00', NULL,
     '{"product_id":"p_009","quantity":1,"address":"上海市浦东新区演示路 1 号"}',
     '示例数据', '2026-10-15 20:30:00'),
    ('ec_20261010_703', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261010003', 'COMPLETED', '已完成', 'PAID',
     '三合一防风冲锋衣 × 1', 659.00, 'CNY', '测试账号', '13800000007',
     '2026-10-10 09:00:00', '2026-10-13 16:00:00',
     '{"product_id":"p_008","quantity":1,"address":"上海市浦东新区演示路 1 号"}',
     '示例数据', '2026-10-10 09:00:00'),
    ('ec_20261005_704', 'u_test', NULL, NULL, 'GOODS', 'ecom-mall', 'EC20261005004', 'CANCELLED', '已取消', NULL,
     'SPF50+ 清爽防晒霜 × 2', 258.00, 'CNY', '测试账号', '13800000007',
     '2026-10-05 15:00:00', NULL,
     '{"product_id":"p_012","quantity":2,"address":"上海市浦东新区演示路 1 号"}',
     '示例数据', '2026-10-05 15:00:00');
