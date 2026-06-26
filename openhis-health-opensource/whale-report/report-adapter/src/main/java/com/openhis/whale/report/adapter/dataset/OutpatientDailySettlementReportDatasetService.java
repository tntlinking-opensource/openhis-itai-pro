/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.dataset;
import com.openhis.whale.report.adapter.support.ReportDatasetTextSupport;

import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门诊日结 C 类报表数据集（OP-05）。
 */
@Service
@RequiredArgsConstructor
public class OutpatientDailySettlementReportDatasetService {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    /**
     * 组装 C 类报表数据集 root Map（rows + summary）。
     *
     * @param params 查询参数（如 dateFrom、dateTo、deptId 等）
     * @return 含 rows、summary 的 root Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        LocalDate dateFrom = resolveDate(params, "dateFrom", "date_from", "billDate", "bill_date");
        LocalDate dateTo = resolveDate(params, "dateTo", "date_to", "billDate", "bill_date");
        if (dateFrom == null) {
            // 缺省日期：当天
            dateFrom = LocalDate.now();
        }
        if (dateTo == null) {
            dateTo = dateFrom;
        }
        if (dateTo.isBefore(dateFrom)) {
            dateTo = dateFrom;
        }

        LocalDateTime from = dateFrom.atStartOfDay();
        LocalDateTime to = dateTo.atTime(LocalTime.MAX);
        List<PaymentReconciliationQueryModel> payments =
                paymentReconciliationQueryService.findPaymentByBillDateRange(from, to);

        Map<String, BigDecimal> channelAmount = new LinkedHashMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        int selfPayCount = 0;
        int insuranceHintCount = 0;

        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null || payment.getKindEnum() != PaymentKind.OUTPATIENT_CLINIC) {
                // 仅统计门诊收费流水
                continue;
            }
            if (payment.getStatusEnum() != PaymentStatus.SUCCESS) {
                // 跳过非成功支付
                continue;
            }
            BigDecimal tendered = safe(payment.getTenderedAmount());
            BigDecimal returned = safe(payment.getReturnedAmount());
            BigDecimal net = tendered.subtract(returned);
            totalIncome = totalIncome.add(net);
            refundTotal = refundTotal.add(returned);

            String channel = StringUtils.isNotEmpty(payment.getPaymentEnum())
                    ? payment.getPaymentEnum().trim()
                    : "OTHER";
            channelAmount.merge(channel, net, BigDecimal::add);

            if (PaymentYbSettlementSupport.hasConfirmed(payment.getYbSettlements())) {
                insuranceHintCount += 1;
            } else {
                selfPayCount += 1;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : channelAmount.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", mapChannelLabel(entry.getKey()));
            row.put("amount", formatMoney(entry.getValue()));
            row.put("unit", "元");
            rows.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scope", "outpatient");
        summary.put("dateRange", formatDateRange(dateFrom, dateTo));
        summary.put("totalIncome", formatMoney(totalIncome));
        summary.put("receivableAmount", formatMoney(totalIncome));
        summary.put("discountAmount", "0.00");
        summary.put("actualAmount", formatMoney(totalIncome));
        summary.put("wechatAmount", formatMoney(channelAmount.getOrDefault("WECHAT", BigDecimal.ZERO)));
        summary.put("cashAmount", formatMoney(channelAmount.getOrDefault("CASH", BigDecimal.ZERO)));
        summary.put("selfPayCount", selfPayCount);
        summary.put("insuranceCount", insuranceHintCount);
        summary.put("refundTotal", formatMoney(refundTotal));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    private static String mapChannelLabel(String channel) {
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

    private static String formatDateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return from.toString();
        }
        return from + "~" + to;
    }

    private static BigDecimal safe(BigDecimal value) {
        return ReportDatasetTextSupport.zeroIfNull(value);
    }

    private static String formatMoney(BigDecimal value) {
        return safe(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static LocalDate resolveDate(Map<String, Object> params, String... keys) {
        if (params == null) {
            return null;
        }
        for (String key : keys) {
            Object raw = params.get(key);
            if (raw instanceof LocalDate date) {
                return date;
            }
            if (raw instanceof String text && StringUtils.isNotEmpty(text)) {
                try {
                    return LocalDate.parse(text.trim());
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return null;
    }
}
