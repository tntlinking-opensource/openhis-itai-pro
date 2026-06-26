/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.support;

import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 门诊/住院日结报表共用：收款员、费用性质筛选与金额汇总。
 */
public final class DailySettlementDatasetSupport {

    private DailySettlementDatasetSupport() {
    }

    /**
     * 从请求参数解析收款员主键（entererId / cashierId / 数字 keyword）。
     *
     * @param params 查询参数
     * @return 收款员主键，未指定时 null
     */
    public static Long resolveEntererId(Map<String, Object> params) {
        Long entererId = ReportDatasetParamSupport.extractLong(
                params,
                "entererId",
                "enterer_id",
                "cashierId",
                "cashier_id");
        if (entererId != null) {
            return entererId;
        }
        return ReportDatasetParamSupport.extractLong(params, "keyword");
    }

    /**
     * 解析费用性质筛选：selfPay（自费）/ insurance（医保）。
     *
     * @param params 查询参数
     * @return 筛选码，未指定时 null
     */
    public static String resolveExpenseProperty(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        for (String key : new String[] {"expenseProperty", "expense_property"}) {
            Object raw = params.get(key);
            if (raw instanceof String text && StringUtils.isNotEmpty(text)) {
                return text.trim();
            }
        }
        return null;
    }

    /**
     * 是否匹配指定收款员；未传 entererId 时恒为 true。
     */
    public static boolean matchesEnterer(PaymentReconciliationQueryModel payment, Long entererId) {
        if (entererId == null) {
            return true;
        }
        if (payment == null || payment.getEntererId() == null) {
            return false;
        }
        return payment.getEntererId().longValue() == entererId.longValue();
    }

    /**
     * 是否匹配费用性质；未传 expenseProperty 时恒为 true。
     */
    public static boolean matchesExpenseProperty(
            PaymentReconciliationQueryModel payment,
            String expenseProperty) {
        if (expenseProperty == null || expenseProperty.isBlank()) {
            return true;
        }
        boolean hasYb = PaymentYbSettlementSupport.hasConfirmed(payment.getYbSettlements());
        if ("insurance".equalsIgnoreCase(expenseProperty)) {
            return hasYb;
        }
        if ("selfPay".equalsIgnoreCase(expenseProperty)) {
            return !hasYb;
        }
        return true;
    }

    /**
     * 是否成功态支付流水。
     */
    public static boolean isSuccessPayment(PaymentReconciliationQueryModel payment) {
        return payment != null && payment.getStatusEnum() == PaymentStatus.SUCCESS;
    }

    /**
     * 净收 = 实收 − 已退。
     */
    public static BigDecimal netAmount(PaymentReconciliationQueryModel payment) {
        BigDecimal tendered = ReportDatasetTextSupport.zeroIfNull(payment.getTenderedAmount());
        BigDecimal returned = ReportDatasetTextSupport.zeroIfNull(payment.getReturnedAmount());
        return tendered.subtract(returned);
    }

  /**
     * 渠道展示名。
     */
    public static String mapChannelLabel(String channel) {
        if ("WECHAT".equalsIgnoreCase(channel)) {
            return "微信";
        }
        if ("CASH".equalsIgnoreCase(channel)) {
            return "现金";
        }
        if ("ALIPAY".equalsIgnoreCase(channel)) {
            return "支付宝";
        }
        return channel;
    }

    /**
     * 金额格式化为两位小数字符串。
     */
    public static String formatMoney(BigDecimal value) {
        BigDecimal safe = ReportDatasetTextSupport.zeroIfNull(value);
        return safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 收款员范围展示文案。
     */
    public static String formatCashierScope(Long entererId) {
        if (entererId == null) {
            return "全部";
        }
        return "指定收款员";
    }

    /**
     * 收款员主键展示（未筛选时返回空串）。
     */
    public static String formatEntererId(Long entererId) {
        if (entererId == null) {
            return "";
        }
        return String.valueOf(entererId);
    }
}
