/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.dataset;
import com.openhis.whale.report.adapter.support.ReportDatasetTextSupport;
import com.openhis.whale.report.adapter.support.ReportDatasetParamSupport;

import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.report.contracts.constants.ReportDatasetLimits;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务类 C 报表数据集（OP-06 / FIN-01 / FIN-02 / FIN-03）。
 */
@Service
@RequiredArgsConstructor
public class FinancialCClassReportDatasetService {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final InvoiceQueryService invoiceQueryService;
    /**
     * 出纳日报 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildCashierDaily(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);
        List<PaymentReconciliationQueryModel> payments = listSuccessPayments(dateFrom, dateTo);

        Map<Long, CashierAgg> byCashier = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null) {
                continue;
            }
            long cashierId = ReportDatasetTextSupport.longOrZero(payment.getEntererId());
            CashierAgg agg = byCashier.computeIfAbsent(cashierId, CashierAgg::new);
            BigDecimal net = netAmount(payment);
            agg.amount = agg.amount.add(net);
            agg.count += 1;
            total = total.add(net);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CashierAgg agg : byCashier.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cashierId", ReportDatasetTextSupport.formatPositiveLongOrDash(agg.cashierId));
            row.put("paymentCount", String.valueOf(agg.count));
            row.put("amount", formatMoney(agg.amount));
            row.put("unit", "元");
            rows.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("cashierCount", byCashier.size());
        summary.put("totalAmount", formatMoney(total));
        return root(rows, summary);
    }
    /**
     * 科室收入 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildDeptRevenue(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);
        List<PaymentReconciliationQueryModel> payments = listSuccessPayments(dateFrom, dateTo);

        Map<Long, BigDecimal> byLocation = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null) {
                continue;
            }
            long locationId = ReportDatasetTextSupport.longOrZero(payment.getLocationId());
            BigDecimal net = netAmount(payment);
            byLocation.merge(locationId, net, BigDecimal::add);
            total = total.add(net);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : byLocation.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deptName", formatDeptKeyLabel(entry.getKey()));
            row.put("incomeAmount", formatMoney(entry.getValue()));
            row.put("unit", "元");
            rows.add(row);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("totalIncome", formatMoney(total));
        return root(rows, summary);
    }
    /**
     * 发票使用 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildInvoiceUsage(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);

        Specification<Invoice> spec = (root, q, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get("issuedAt"), ReportDatasetParamSupport.startOf(dateFrom)));
            predicates.add(cb.lessThanOrEqualTo(
                    root.get("issuedAt"), ReportDatasetParamSupport.endOf(dateTo)));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<Invoice> invoices = invoiceQueryService.listInvoiceEntities(spec);
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int truncated = 0;
        for (Invoice invoice : invoices) {
            if (invoice == null) {
                continue;
            }
            if (rows.size() >= ReportDatasetLimits.MAX_TABULAR_ROWS) {
                // 发票明细超上限，截断并记录剩余条数
                truncated = invoices.size() - rows.size();
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("busNo", StringUtils.defaultIfBlank(invoice.getBusNo(), "—"));
            row.put("typeCode", StringUtils.defaultIfBlank(invoice.getTypeCode(), "—"));
            row.put("statusEnum", StringUtils.defaultIfBlank(invoice.getStatusEnum(), "—"));
            row.put("amount", formatMoney(invoice.getAmount()));
            row.put("issuedAt", ReportDatasetTextSupport.formatObjectOrDash(invoice.getIssuedAt()));
            rows.add(row);
            if (invoice.getAmount() != null) {
                total = total.add(invoice.getAmount());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("invoiceCount", invoices.size());
        summary.put("totalAmount", formatMoney(total));
        if (truncated > 0) {
            summary.put("truncated", true);
            summary.put("truncatedHint", "仅展示前 " + ReportDatasetLimits.MAX_TABULAR_ROWS + " 条");
        }
        return root(rows, summary);
    }
    /**
     * 医保对账 C 类报表。
     *
     * @param params 查询或业务参数
     * @return 含 rows、summary 等的 root Map
     */

    public Map<String, Object> buildInsuranceReconciliation(Map<String, Object> params) {
        LocalDate dateFrom = ReportDatasetParamSupport.resolveDateRangeFrom(params, LocalDate.now());
        LocalDate dateTo = ReportDatasetParamSupport.resolveDateRangeTo(params, dateFrom);
        List<PaymentReconciliationQueryModel> payments = listSuccessPayments(dateFrom, dateTo);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null || !PaymentYbSettlementSupport.hasConfirmed(payment.getYbSettlements())) {
                continue;
            }
            if (rows.size() >= ReportDatasetLimits.MAX_TABULAR_ROWS) {
                // 医保对账行数达上限
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("paymentNo", StringUtils.defaultIfBlank(payment.getPaymentNo(), "—"));
            String kindEnum = "—";
            if (payment.getKindEnum() != null) {
                kindEnum = payment.getKindEnum().getCode();
            }
            row.put("kindEnum", kindEnum);
            row.put("ybSettleIds", PaymentYbSettlementSupport.joinSetlIds(payment.getYbSettlements()));
            row.put("tenderedAmount", formatMoney(payment.getTenderedAmount()));
            row.put("returnedAmount", formatMoney(payment.getReturnedAmount()));
            row.put("billDate", ReportDatasetTextSupport.formatObjectOrDash(payment.getBillDate()));
            rows.add(row);
            total = total.add(netAmount(payment));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", ReportDatasetParamSupport.formatDateRange(dateFrom, dateTo));
        summary.put("ybPaymentCount", rows.size());
        summary.put("netAmount", formatMoney(total));
        return root(rows, summary);
    }

    private List<PaymentReconciliationQueryModel> listSuccessPayments(LocalDate dateFrom, LocalDate dateTo) {
        List<PaymentReconciliationQueryModel> payments = paymentReconciliationQueryService.findPaymentByBillDateRange(
                ReportDatasetParamSupport.startOf(dateFrom),
                ReportDatasetParamSupport.endOf(dateTo));
        List<PaymentReconciliationQueryModel> success = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment != null && payment.getStatusEnum() == PaymentStatus.SUCCESS) {
                success.add(payment);
            }
        }
        return success;
    }

    private static BigDecimal netAmount(PaymentReconciliationQueryModel payment) {
        BigDecimal tendered = ReportDatasetTextSupport.zeroIfNull(payment.getTenderedAmount());
        BigDecimal returned = ReportDatasetTextSupport.zeroIfNull(payment.getReturnedAmount());
        return tendered.subtract(returned);
    }

    private static Map<String, Object> root(List<Map<String, Object>> rows, Map<String, Object> summary) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        root.put("summary", summary);
        return root;
    }

    private static String formatDeptKeyLabel(long deptKey) {
        if (deptKey > 0) {
            return "科室#" + deptKey;
        }
        return "—";
    }

    private static String formatMoney(BigDecimal value) {
        BigDecimal safe = ReportDatasetTextSupport.zeroIfNull(value);
        return safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static final class CashierAgg {
        private final long cashierId;
        private int count = 0;
        private BigDecimal amount = BigDecimal.ZERO;

        private CashierAgg(long cashierId) {
            this.cashierId = cashierId;
        }
    }
}
