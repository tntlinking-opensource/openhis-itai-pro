/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.dataset;
import com.openhis.whale.report.adapter.support.ReportDatasetTextSupport;

import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院日结 C 类报表数据集（IP-34）。
 */
@Service
@RequiredArgsConstructor
public class InpatientDailySettlementReportDatasetService {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final EncounterContextFacade encounterContextFacade;

    /**
     * 组装 C 类报表数据集 root Map（rows + summary）。
     *
     * @param params 查询参数（如 dateFrom、dateTo、deptId 等）
     * @return 含 rows、summary 的 root Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        LocalDate dateFrom = resolveDate(params, "dateFrom", "date_from");
        LocalDate dateTo = resolveDate(params, "dateTo", "date_to");
        if (dateFrom == null) {
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

        Set<Long> encounterIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null || payment.getEncounterId() == null) {
                continue;
            }
            PaymentKind kind = payment.getKindEnum();
            if (kind != PaymentKind.INPATIENT_CLINIC && kind != PaymentKind.HOSPITAL_DEPOSIT) {
                // 仅住院收费与预交金
                continue;
            }
            if (payment.getStatusEnum() != PaymentStatus.SUCCESS) {
                // 跳过非成功支付
                continue;
            }
            encounterIds.add(payment.getEncounterId());
        }

        Map<Long, String> wardByEncounter = indexWardNames(encounterIds);

        Map<String, BigDecimal> amountByWardCategory = new LinkedHashMap<>();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        int selfPayCount = 0;
        int insuranceHintCount = 0;

        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null) {
                continue;
            }
            PaymentKind kind = payment.getKindEnum();
            if (kind != PaymentKind.INPATIENT_CLINIC && kind != PaymentKind.HOSPITAL_DEPOSIT) {
                // 仅住院收费与预交金
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

            String wardName = resolveWardName(payment, wardByEncounter);
            String category = resolveCategoryLabel(payment);
            String key = wardName + "|" + category;
            amountByWardCategory.merge(key, net, BigDecimal::add);

            if (PaymentYbSettlementSupport.hasConfirmed(payment.getYbSettlements())) {
                insuranceHintCount += 1;
            } else {
                selfPayCount += 1;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : amountByWardCategory.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("wardName", parts[0]);
            row.put("category", resolveCategoryFromKey(entry.getKey()));
            row.put("amount", formatMoney(entry.getValue()));
            row.put("unit", "元");
            rows.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scope", "inpatient");
        summary.put("dateRange", formatDateRange(dateFrom, dateTo));
        summary.put("totalIncome", formatMoney(totalIncome));
        summary.put("receivableAmount", formatMoney(totalIncome));
        summary.put("discountAmount", "0.00");
        summary.put("actualAmount", formatMoney(totalIncome));
        summary.put("refundTotal", formatMoney(refundTotal));
        summary.put("selfPayCount", selfPayCount);
        summary.put("insuranceCount", insuranceHintCount);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    private Map<Long, String> indexWardNames(Set<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ordered = new ArrayList<>(encounterIds);
        List<InpatientEncounterContextQueryModel> contexts =
                encounterContextFacade.listInpatientContextsByEncounterIdsOrdered(ordered);
        Map<Long, String> wardByEncounter = new LinkedHashMap<>();
        for (InpatientEncounterContextQueryModel context : contexts) {
            if (context == null || context.getEncounterId() == null) {
                continue;
            }
            String wardName = "未关联病区";
            if (StringUtils.isNotEmpty(context.getWardName())) {
                wardName = context.getWardName().trim();
            }
            wardByEncounter.put(context.getEncounterId(), wardName);
        }
        return wardByEncounter;
    }

    private static String resolveWardName(
            PaymentReconciliationQueryModel payment,
            Map<Long, String> wardByEncounter) {
        Long encounterId = payment.getEncounterId();
        if (encounterId != null && wardByEncounter.containsKey(encounterId)) {
            return wardByEncounter.get(encounterId);
        }
        if (payment.getKindEnum() == PaymentKind.HOSPITAL_DEPOSIT) {
            return "预交金";
        }
        if (encounterId != null) {
            return "未关联病区";
        }
        return "—";
    }

    private static String resolveCategoryFromKey(String key) {
        String[] parts = key.split("\\|", 2);
        if (parts.length > 1) {
            return parts[1];
        }
        return "—";
    }

    private static String resolveCategoryLabel(PaymentReconciliationQueryModel payment) {
        String channel = "OTHER";
        if (StringUtils.isNotEmpty(payment.getPaymentEnum())) {
            channel = payment.getPaymentEnum().trim();
        }
        String channelLabel = mapChannelLabel(channel);
        if (payment.getKindEnum() == PaymentKind.HOSPITAL_DEPOSIT) {
            return "预交金-" + channelLabel;
        }
        return channelLabel;
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
