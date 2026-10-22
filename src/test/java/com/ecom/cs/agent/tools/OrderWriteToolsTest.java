package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.apikey.service.ApiKeyService;
import com.ecom.cs.business.order.entity.EcommerceOrder;
import com.ecom.cs.business.order.entity.OrderStatus;
import com.ecom.cs.business.order.entity.OrderType;
import com.ecom.cs.business.order.repo.EcommerceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderWriteTools 单元测试：
 * parseCliResponse 的成功/失败分支解析，以及 cancel_order 的归属校验、幂等、
 * 各业务类型取消分支（不实际执行 CLI，通过"未配置 API Key"路径覆盖平台失败分支）。
 *
 * @author Hollis
 */
@ExtendWith(MockitoExtension.class)
class OrderWriteToolsTest {

    private static final String USER_ID = "u_test";

    @Mock
    private EcommerceOrderRepository orderRepository;

    @Mock
    private ApiKeyService apiKeyService;

    private OrderWriteTools tools;
    private AgentSessionContext ctx;

    @BeforeEach
    void setUp() {
        tools = new OrderWriteTools(orderRepository, apiKeyService);
        ctx = new AgentSessionContext(USER_ID, "s_test");
    }

    private EcommerceOrder record(String orderNo, OrderType bizType, OrderStatus status, String externalOrderNo) {
        EcommerceOrder r = new EcommerceOrder();
        r.setOrderNo(orderNo);
        r.setUserId(USER_ID);
        r.setBizType(bizType);
        r.setStatus(status);
        r.setExternalOrderNo(externalOrderNo);
        return r;
    }

    // ============ parseCliResponse ============

    @Test
    void parseCliResponse_flightSuccess() {
        OrderWriteTools.CliResult result = tools.parseCliResponse(
                "{\"successCode\": true, \"msg\": \"订单取消成功！\", \"errorCode\": 170000}");
        assertTrue(result.success());
    }

    @Test
    void parseCliResponse_trainSuccess() {
        OrderWriteTools.CliResult result = tools.parseCliResponse(
                "{\"successCode\": true, \"errorMessage\": null}");
        assertTrue(result.success());
    }

    @Test
    void parseCliResponse_failWithMsg() {
        OrderWriteTools.CliResult result = tools.parseCliResponse(
                "{\"successCode\": false, \"msg\": \"订单状态不允许取消\"}");
        assertFalse(result.success());
        assertEquals("订单状态不允许取消", result.errorMsg());
    }

    @Test
    void parseCliResponse_failWithErrorMessage() {
        OrderWriteTools.CliResult result = tools.parseCliResponse(
                "{\"successCode\": false, \"errorMessage\": \"车票已出票\"}");
        assertFalse(result.success());
        assertEquals("车票已出票", result.errorMsg());
    }

    @Test
    void parseCliResponse_failWithoutReason() {
        OrderWriteTools.CliResult result = tools.parseCliResponse("{\"successCode\": false}");
        assertFalse(result.success());
        assertEquals("平台返回取消失败（未提供具体原因）", result.errorMsg());
    }

    @Test
    void parseCliResponse_jsonWrappedInLogs_stillParsed() {
        OrderWriteTools.CliResult result = tools.parseCliResponse(
                "[INFO] calling...\n{\"successCode\": true}\ndone");
        assertTrue(result.success());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no json here"})
    void parseCliResponse_invalidOutput_fails(String output) {
        assertFalse(tools.parseCliResponse(output).success());
    }

    // ============ parseCliResponse：MCP 封装格式 ============

    /**
     * 线上真实场景回归：tuniu CLI 输出 MCP 封装格式，业务响应（success 字段）以
     * 字符串形式嵌套在 result.structuredContent.result 中，此前被误判为取消失败。
     */
    @Test
    void parseCliResponse_mcpWrappedFlightSuccess() {
        String biz = "{\"success\": true, \"msg\": \"订单取消成功！\", \"errorCode\": 170000, \"atd\": null, \"data\": null}";
        String output = """
                {
                  "success": true,
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": "%s"
                      }
                    ],
                    "structuredContent": {
                      "result": "%s"
                    },
                    "isError": false
                  },
                  "metadata": {
                    "server": "flight",
                    "tool": "cancelOrder",
                    "latency_ms": 3344
                  }
                }""".formatted(biz.replace("\"", "\\\""), biz.replace("\"", "\\\""));
        OrderWriteTools.CliResult result = tools.parseCliResponse(output);
        assertTrue(result.success());
    }

    @Test
    void parseCliResponse_mcpWrappedBusinessFail() {
        String biz = "{\"success\": false, \"msg\": \"订单状态不允许取消\", \"errorCode\": 170001}";
        String output = """
                {
                  "success": true,
                  "result": {
                    "structuredContent": {"result": "%s"},
                    "isError": false
                  },
                  "metadata": {}
                }""".formatted(biz.replace("\"", "\\\""));
        OrderWriteTools.CliResult result = tools.parseCliResponse(output);
        assertFalse(result.success());
        assertEquals("订单状态不允许取消", result.errorMsg());
    }

    @Test
    void parseCliResponse_mcpWrappedWithoutStructuredContent_fallbackToText() {
        String output = """
                {
                  "success": true,
                  "result": {
                    "content": [
                      {"type": "text", "text": "{\\"success\\": true, \\"msg\\": \\"订单取消成功！\\"}"}
                    ],
                    "isError": false
                  }
                }""";
        OrderWriteTools.CliResult result = tools.parseCliResponse(output);
        assertTrue(result.success());
    }

    @Test
    void parseCliResponse_mcpCliLevelFail() {
        String output = """
                {
                  "success": false,
                  "error": "Tool not found: cancelOrder"
                }""";
        OrderWriteTools.CliResult result = tools.parseCliResponse(output);
        assertFalse(result.success());
        assertEquals("Tool not found: cancelOrder", result.errorMsg());
    }

    @Test
    void parseCliResponse_directSuccessField_legacyStillSupported() {
        // 业务响应直出且使用现行 success 字段（无 Code 后缀）
        OrderWriteTools.CliResult result = tools.parseCliResponse(
                "{\"success\": true, \"msg\": \"订单取消成功！\", \"errorCode\": 170000}");
        assertTrue(result.success());
    }

    // ============ cancel_order：参数与归属校验 ============

    @Test
    void cancel_blankOrderNo_rejected() {
        JSONObject o = JSON.parseObject(tools.cancelOrder(ctx, " ", null));
        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_PARAM", o.getString("errorCode"));
    }

    @Test
    void cancel_notFound_rejected() {
        when(orderRepository.findByOrderNo("ec_404")).thenReturn(Optional.empty());
        JSONObject o = JSON.parseObject(tools.cancelOrder(ctx, "ec_404", null));
        assertFalse(o.getBooleanValue("success"));
        assertEquals("BOOKING_NOT_FOUND", o.getString("errorCode"));
    }

    @Test
    void cancel_wrongOwner_permissionDenied() {
        EcommerceOrder other = record("ec_1", OrderType.FLIGHT, OrderStatus.CONFIRMED, "TN001");
        other.setUserId("u_other");
        when(orderRepository.findByOrderNo("ec_1")).thenReturn(Optional.of(other));

        JSONObject o = JSON.parseObject(tools.cancelOrder(ctx, "ec_1", null));

        assertFalse(o.getBooleanValue("success"));
        assertEquals("PERMISSION_DENIED", o.getString("errorCode"));
        verify(orderRepository, never()).save(any());
    }

    // ============ cancel_order：幂等与业务分支 ============

    @Test
    void cancel_alreadyCancelled_idempotentSuccess() {
        EcommerceOrder cancelled = record("ec_1", OrderType.FLIGHT, OrderStatus.CANCELLED, "TN001");
        when(orderRepository.findByOrderNo("ec_1")).thenReturn(Optional.of(cancelled));

        JSONObject o = JSON.parseObject(tools.cancelOrder(ctx, "ec_1", null));

        assertTrue(o.getBooleanValue("success"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancel_hotel_marksInternalOnlyAndKeepsReason() {
        EcommerceOrder hotel = record("ec_1", OrderType.HOTEL, OrderStatus.CONFIRMED, "TN002");
        when(orderRepository.findByOrderNo("ec_1")).thenReturn(Optional.of(hotel));

        JSONObject o = JSON.parseObject(tools.cancelOrder(ctx, "ec_1", "行程有变"));

        assertTrue(o.getBooleanValue("success"));
        assertFalse(o.getBooleanValue("platformCancelled"));
        assertEquals(OrderStatus.CANCELLED, hotel.getStatus());
        assertEquals("行程有变", hotel.getRemark());
        verify(orderRepository).save(hotel);
    }

    @Test
    void cancel_flightWithoutExternalOrderNo_marksInternalOnly() {
        EcommerceOrder flight = record("ec_1", OrderType.FLIGHT, OrderStatus.CREATED, null);
        when(orderRepository.findByOrderNo("ec_1")).thenReturn(Optional.of(flight));

        JSONObject o = JSON.parseObject(tools.cancelOrder(ctx, "ec_1", null));

        assertTrue(o.getBooleanValue("success"));
        assertFalse(o.getBooleanValue("platformCancelled"));
        assertEquals(OrderStatus.CANCELLED, flight.getStatus());
    }

    @Test
    void cancel_flightPlatformFail_internalStateUntouched() {
        // 未配置 API Key → 平台取消失败 → 内部状态不得变更、不得落库
        EcommerceOrder flight = record("ec_1", OrderType.FLIGHT, OrderStatus.CONFIRMED, "TN003");
        when(orderRepository.findByOrderNo("ec_1")).thenReturn(Optional.of(flight));
        when(apiKeyService.getApiKey(USER_ID, "tuniu-cli")).thenReturn(null);

        JSONObject o = JSON.parseObject(tools.cancelOrder(ctx, "ec_1", null));

        assertFalse(o.getBooleanValue("success"));
        assertEquals("PLATFORM_CANCEL_FAILED", o.getString("errorCode"));
        assertEquals(OrderStatus.CONFIRMED, flight.getStatus());
        verify(orderRepository, never()).save(any());
    }
}
