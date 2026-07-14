/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleQueryService;
import com.openhis.chs.domain.reconciliation.command.SummarizeClearAmountCommand;
import com.openhis.chs.domain.reconciliation.result.ClearAmountSummaryResult;
import com.openhis.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.openhis.chs.domain.reconciliation.support.ReconDetailRowSupport;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 对账清算本地结算快照只读查询（汇总金额、明细行预览） */
@Service
@RequiredArgsConstructor
public class YbReconciliationQueryService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbInpatientPreSettleQueryService inpatientPreSettleQueryService;

    /**
     * 汇总指定日期区间内门诊与住院结算快照金额（3203A 申请前预览）。
     *
     * @param command  契约与日期区间
     * @param tenantId 租户
     * @return 医疗费、基金、账户、现金及结算笔数合计
     */
    public ClearAmountSummaryResult summarizeClearAmounts(SummarizeClearAmountCommand command, Long tenantId) {
        DateRange range = resolveDateRange(command);
        BigDecimal medfee = BigDecimal.ZERO;
        BigDecimal fund = BigDecimal.ZERO;
        BigDecimal acct = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        int count = 0;

        for (YbSettlementRecord row : settlementRecordQueryService.listCreatedBetween(
            tenantId, range.beginAt(), range.endExclusive())) {
            medfee = medfee.add(nullToZero(row.getMedfeeSumamt()));
            fund = fund.add(nullToZero(row.getFundPaySumamt()));
            acct = acct.add(nullToZero(row.getAcctPay()));
            cash = cash.add(nullToZero(row.getPsnCashPay()));
            count += 1;
        }
        for (YbInpatientPreSettle row : inpatientPreSettleQueryService.listSettledBetween(
            tenantId, range.beginAt(), range.endExclusive())) {
            medfee = medfee.add(nullToZero(row.getMedfeeSumamt()));
            fund = fund.add(nullToZero(row.getFundPaySumamt()));
            acct = acct.add(nullToZero(row.getAcctPay()));
            cash = cash.add(nullToZero(row.getPsnCashPay()));
            count += 1;
        }
        return new ClearAmountSummaryResult()
            .setMedfeeSumamt(medfee)
            .setMedSumfee(medfee)
            .setFundAppySum(fund)
            .setAcctPay(acct)
            .setCashPayamt(cash)
            .setSetlCount(count);
    }

    /**
     * 加载 3202 明细对账输入文件行预览（门诊 + 住院结算快照）。
     *
     * @param command  契约与日期区间
     * @param tenantId 租户
     * @return 3202 输入文件行列表
     */
    public List<ReconDetailRowResult> loadDetailRows(SummarizeClearAmountCommand command, Long tenantId) {
        DateRange range = resolveDateRange(command);
        List<ReconDetailRowResult> rows = new ArrayList<>();
        rows.addAll(ReconDetailRowSupport.fromOutpatientRows(
            settlementRecordQueryService.listCreatedBetween(tenantId, range.beginAt(), range.endExclusive())));
        rows.addAll(ReconDetailRowSupport.fromInpatientRows(
            inpatientPreSettleQueryService.listSettledBetween(tenantId, range.beginAt(), range.endExclusive())));
        return rows;
    }

    /**
     * 解析并校验汇总/预览请求的日期区间。
     *
     * @param command 含 begndate / enddate 的命令
     * @return 左闭右开时间区间
     */
    private DateRange resolveDateRange(SummarizeClearAmountCommand command) {
        SummarizeClearAmountCommand safe = command == null ? new SummarizeClearAmountCommand() : command;
        LocalDate begin = parseDate(safe.getBegndate(), "开始日期");
        LocalDate end = parseDate(safe.getEnddate(), "结束日期");
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束日期不能早于开始日期");
        }
        return new DateRange(begin.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    /**
     * 解析 yyyy-MM-dd 日期字符串。
     *
     * @param text  日期文本
     * @param label 字段标签（校验失败提示用）
     * @return 解析后的日期
     */
    private static LocalDate parseDate(String text, String label) {
        if (isEmpty(text)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, label + "不能为空");
        }
        return LocalDate.parse(text.trim(), DATE_FMT);
    }

    /** {@code null} 金额视为 {@link BigDecimal#ZERO} */
    private static BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    /** 左闭右开日期区间（endExclusive 不含当日 24:00 之后） */
    private record DateRange(LocalDateTime beginAt, LocalDateTime endExclusive) {
    }
}
