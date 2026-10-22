package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.agent.tools.model.ConflictItem;
import com.ecom.cs.agent.tools.model.ConflictReport;
import com.ecom.cs.agent.tools.model.ConflictSeverity;
import com.ecom.cs.agent.tools.model.ConflictType;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 售后工单重复检测工具集。
 *
 * <p>提供 {@code check_duplicate_after_sale_ticket} 工具，覆盖两类检查：</p>
 * <ol>
 *   <li><b>完全重复</b>：同商品 + 同购买日期 + 同问题描述已存在生效中工单（HIGH）。</li>
 *   <li><b>疑似重复</b>：同一商品已存在其他生效中工单，可能重复售后（MEDIUM）。</li>
 * </ol>
 *
 * <p>该工具为"建议"性质——是否真的阻断提交由 MasterAgent / LLM 决定；
 * TicketManageAgent 仍可在告知用户重复后由用户确认继续提交。</p>
 *
 * @author Hollis
 */
@Component
public class AfterSaleTicketDuplicateTools {

    private static final Logger logger = LoggerFactory.getLogger(AfterSaleTicketDuplicateTools.class);

    /** 视为"同一商品"的最短名称包含长度，避免过短的商品名误判 */
    private static final int MIN_NAME_MATCH_LENGTH = 4;

    @Autowired
    private AfterSaleTicketRepository ticketRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 重复工单检测
    // ─────────────────────────────────────────────────────────────────────────

    @Tool(name = "check_duplicate_after_sale_ticket",
            description = "检查用户当前要提交/修改的售后工单是否与该用户已有的生效中工单（DRAFT/SUBMITTED/APPROVED）重复。"
                    + "覆盖两类检查：① 同商品 + 同购买日期 + 同问题描述 = 明确重复（DUPLICATE_TICKET / HIGH）；"
                    + "② 同一商品存在其他生效工单 = 疑似重复（SAME_PRODUCT_ACTIVE / MEDIUM）。"
                    + "返回 JSON 结构 {has_conflict, total_conflicts, conflicts:[...], summary}。"
                    + "建议在每次 submit_after_sale_ticket 之前、以及修改售后工单的商品名称时调用。")
    public String checkDuplicateAfterSaleTicket(
            AgentSessionContext sessionCtx,
            @ToolParam(name = "product_name", description = "本次要申请售后的商品或服务名称，含规格与数量，如「主动降噪无线蓝牙耳机 Pro 黑色 1件」") String productName,
            @ToolParam(name = "issue_desc", description = "本次要提交的问题描述与期望处理方式，如「右耳偶发断连，申请换货」", required = false) String issueDesc,
            @ToolParam(name = "purchase_date", description = "本次要提交的购买日期，YYYY-MM-DD", required = false) String purchaseDate,
            @ToolParam(name = "exclude_order_id", description = "需要排除的售后工单ID（修改场景下排除自身），可选", required = false) String excludeOrderId) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][check_duplicate_after_sale_ticket] userId={}, productName={}, purchaseDate={}, exclude={}",
                userId, productName, purchaseDate, excludeOrderId);

        // ── 入参校验 ──────────────────────────────────────────────────────────
        List<String> errors = new ArrayList<>();
        if (isBlank(userId)) {
            errors.add("user_id 不能为空");
        }
        if (isBlank(productName)) {
            errors.add("product_name（商品名称）不能为空");
        }
        if (!errors.isEmpty()) {
            return JSON.toJSONString(new ConflictReport() {{
                setHasConflict(false);
                setSummary("入参校验失败：" + String.join("；", errors));
            }});
        }

        // ── 查询已有生效中工单 ───────────────────────────────────────────────
        Set<AfterSaleTicketStatus> activeStatuses = Set.of(
                AfterSaleTicketStatus.DRAFT, AfterSaleTicketStatus.SUBMITTED, AfterSaleTicketStatus.APPROVED);
        List<AfterSaleTicket> candidates = ticketRepository.findActiveByUserId(userId, activeStatuses);

        // 排除自身（修改场景）
        if (!isBlank(excludeOrderId)) {
            candidates.removeIf(t -> excludeOrderId.equals(t.getOrderId()));
        }

        // ── 重复检测 ────────────────────────────────────────────────────────
        List<ConflictItem> conflicts = new ArrayList<>();
        for (AfterSaleTicket existing : candidates) {
            detectDuplicateFor(existing, productName, issueDesc, purchaseDate, conflicts);
        }

        // 按严重等级降序：HIGH > MEDIUM > LOW
        conflicts.sort(Comparator.comparingInt((ConflictItem c) -> c.getSeverity().rank())
                .thenComparing(c -> c.getOrderId() == null ? "" : c.getOrderId()));

        ConflictReport report = new ConflictReport();
        report.setConflicts(conflicts);
        report.setTotalConflicts(conflicts.size());
        report.setHasConflict(!conflicts.isEmpty());
        report.setSummary(buildSummary(conflicts, productName, purchaseDate));

        logger.info("[TOOL][check_duplicate_after_sale_ticket] userId={} 命中重复 {} 条", userId, conflicts.size());
        return JSON.toJSONString(report);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 单条已有工单 vs 候选工单的重复检测
    // ─────────────────────────────────────────────────────────────────────────

    private void detectDuplicateFor(AfterSaleTicket existing,
                                    String productName,
                                    String issueDesc,
                                    String purchaseDate,
                                    List<ConflictItem> out) {
        if (!isSameProduct(existing.getProductName(), productName)) {
            return;
        }

        boolean samePurchaseDate = isBlank(purchaseDate) || isBlank(existing.getPurchaseDate())
                || existing.getPurchaseDate().equals(purchaseDate);
        boolean sameIssue = !isBlank(issueDesc) && !isBlank(existing.getIssueDesc())
                && normalize(existing.getIssueDesc()).equals(normalize(issueDesc));

        ConflictItem item = new ConflictItem();
        item.setOrderId(existing.getOrderId());
        item.setOrderSummary(buildTicketSummary(existing));

        if (samePurchaseDate && sameIssue) {
            item.setType(ConflictType.DUPLICATE_TICKET);
            item.setSeverity(ConflictSeverity.HIGH);
            item.setDescription(String.format(
                    "已有售后工单（%s，购买日期 %s）针对同一商品提交了相同的问题描述，属于重复提交。",
                    safe(existing.getProductName()), safe(existing.getPurchaseDate())));
            item.setSuggestion("无需重复提交，请直接告知用户该工单已存在并说明当前处理进度；"
                    + "如需补充信息，请改用修改工单的方式。");
        } else {
            item.setType(ConflictType.SAME_PRODUCT_ACTIVE);
            item.setSeverity(ConflictSeverity.MEDIUM);
            item.setDescription(String.format(
                    "该商品已有生效中的售后工单（%s，购买日期 %s，当前状态 %s），本次提交的问题不同。",
                    safe(existing.getProductName()), safe(existing.getPurchaseDate()), statusCode(existing)));
            item.setSuggestion("请向用户确认这是同一商品的另一个问题还是重复提交；"
                    + "确认是新问题后可继续提交，但建议在工单描述中说明与前一张工单的区别。");
        }
        out.add(item);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────────────

    private static String buildTicketSummary(AfterSaleTicket t) {
        return String.format("%s，购买日期 %s（%s）",
                safe(t.getProductName()), safe(t.getPurchaseDate()), statusCode(t));
    }

    private static String statusCode(AfterSaleTicket t) {
        return t.getStatus() == null ? "-" : t.getStatus().getCode();
    }

    private static String buildSummary(List<ConflictItem> conflicts, String productName, String purchaseDate) {
        if (conflicts.isEmpty()) {
            return String.format("已检查用户已有售后工单，未发现与「%s」（购买日期 %s）重复的工单。",
                    productName, isBlank(purchaseDate) ? "-" : purchaseDate);
        }
        long high = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.HIGH).count();
        long medium = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.MEDIUM).count();
        long low = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.LOW).count();
        return String.format("命中 %d 条重复记录（HIGH=%d, MEDIUM=%d, LOW=%d），请逐条阅读 description 与 suggestion。",
                conflicts.size(), high, medium, low);
    }

    /**
     * 判断两条商品名称是否指向同一商品：归一化后互相包含即视为同一商品。
     */
    private static boolean isSameProduct(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        if (Math.min(na.length(), nb.length()) < MIN_NAME_MATCH_LENGTH) {
            return false;
        }
        return na.contains(nb) || nb.contains(na);
    }

    /**
     * 归一化：去掉所有空白与标点、统一大小写，便于宽松比较。
     * <p>{@code \p{P}} 覆盖中英文标点（，。、！？；：（）【】引号等），
     * 使用户口述与工单记录的细微标点差异不影响重复判定。</p>
     */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s　]+", "")
                .replaceAll("\\p{P}", "")
                .toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
