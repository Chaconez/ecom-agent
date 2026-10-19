package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.business.booking.entity.BookingRecord;
import com.ecom.cs.business.booking.entity.BookingStatus;
import com.ecom.cs.business.booking.entity.BookingType;
import com.ecom.cs.business.booking.repo.BookingRecordRepository;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 外部预订记录查询工具。
 *
 * <p>面向用户查询其通过 Agent 下单的机票/酒店/火车票等预订记录（{@code booking_record} 表），
 * 支持按内部预订单号精确查询，或按业务类型 / 预订状态过滤列表。数据始终以会话上下文中的
 * {@code userId} 做租户隔离，避免越权查询他人订单。</p>
 *
 * @author Hollis
 */
@Component
public class BookingReadTools {

    private static final Logger logger = LoggerFactory.getLogger(BookingReadTools.class);

    @Autowired
    private BookingRecordRepository bookingRecordRepository;

    @Tool(name = "query_booking_record",
            description = "查询用户的订单记录（商品订单等）。可按内部订单号 booking_id 精确查询详情，"
                    + "或按关联工单ID travel_order_id 查询该工单关联的所有订单，"
                    + "或按业务类型 biz_type、订单状态 status 过滤列表；均不传则返回该用户全部订单记录")
    public String queryBookingRecord(
            AgentSessionContext sessionCtx,
            @ToolParam(name = "booking_id", description = "内部订单号（系统生成，如 bk_xxx），可选", required = false) String bookingId,
            @ToolParam(name = "travel_order_id", description = "关联工单ID，传入则查询该工单关联的所有订单记录，可选", required = false) String travelOrderId,
            @ToolParam(name = "biz_type", description = "业务类型过滤：GOODS(商品)/FLIGHT(机票)/HOTEL(酒店)/TRAIN(火车票)，可选", required = false) String bizType,
            @ToolParam(name = "status", description = "订单状态过滤：CREATED/PENDING_PAYMENT/PAID/CONFIRMED/COMPLETED/CANCELLED/REFUNDED/FAILED，可选", required = false) String status) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][query_booking_record] userId={}, bookingId={}, travelOrderId={}, bizType={}, status={}", userId, bookingId, travelOrderId, bizType, status);

        // 1. 按内部预订单号精确查询（做租户校验，避免越权）
        if (bookingId != null && !bookingId.isBlank()) {
            BookingRecord record = bookingRecordRepository.findByBookingId(bookingId)
                    .filter(r -> userId != null && userId.equals(r.getUserId()))
                    .orElse(null);
            String result = JSON.toJSONString(record != null ? record : Map.of("error", "booking not found"));
            logger.info("[TOOL][query_booking_record] result={}", result);
            return result;
        }

        // 2. 按差旅单ID查询关联预订
        if (travelOrderId != null && !travelOrderId.isBlank()) {
            List<BookingRecord> records;

            if (bizType == null) {
                records = bookingRecordRepository.findByUserIdAndTravelOrderId(userId, travelOrderId);
            } else {
                BookingType bt = BookingType.of(bizType);
                records = bookingRecordRepository.findByUserIdAndTravelOrderId(userId, travelOrderId, bt);
            }

            if (status != null && !status.isBlank()) {
                BookingStatus bs = BookingStatus.of(status);
                if (bs != null) {
                    records = records.stream().filter(r -> r.getStatus() == bs).collect(Collectors.toList());
                }
            }
            String result = JSON.toJSONString(records);
            logger.info("[TOOL][query_booking_record] byTravelOrderId={}, count={}", travelOrderId, records.size());
            return result;
        }

        // 3. 校验业务类型参数
        BookingType bizTypeEnum = null;
        if (bizType != null && !bizType.isBlank()) {
            bizTypeEnum = BookingType.of(bizType);
            if (bizTypeEnum == null) {
                return JSON.toJSONString(Map.of("error",
                        "无效的业务类型参数: " + bizType + "，可选值: GOODS/FLIGHT/HOTEL/TRAIN"));
            }
        }

        // 4. 校验状态参数
        BookingStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = BookingStatus.of(status);
            if (statusEnum == null) {
                return JSON.toJSONString(Map.of("error",
                        "无效的状态参数: " + status
                                + "，可选值: CREATED/PENDING_PAYMENT/PAID/CONFIRMED/COMPLETED/CANCELLED/REFUNDED/FAILED"));
            }
        }

        // 5. 查询列表（按业务类型或全部），再按状态在内存过滤
        List<BookingRecord> records = bizTypeEnum != null
                ? bookingRecordRepository.findByUserIdAndBizType(userId, bizTypeEnum)
                : bookingRecordRepository.findByUserId(userId);

        if (statusEnum != null) {
            final BookingStatus target = statusEnum;
            records = records.stream()
                    .filter(r -> r.getStatus() == target)
                    .collect(Collectors.toList());
        }

        String result = JSON.toJSONString(records);
        logger.info("[TOOL][query_booking_record] count={}", records.size());
        return result;
    }
}
