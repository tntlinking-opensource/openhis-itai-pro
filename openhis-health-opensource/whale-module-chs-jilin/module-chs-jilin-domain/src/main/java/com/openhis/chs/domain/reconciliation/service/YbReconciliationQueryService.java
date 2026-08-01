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
import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.chs.domain.settlement.service.YbSettlementReversalQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对账清算本地结算快照只读查询（按险种×清算类别汇总金额、明细行预览）。
 * <p>数据源仅为本院 HIS 已落库结算快照与撤销凭证；<b>不访问医保网关、不发起 3201/3202</b>。
 * 平台对账由 bridge Invoker / Workflow 在用户点击「发起对账」时另行调用。</p>
 * <p>跨聚合读：经 settlement / inpatient 本模块 QueryService 装载快照，不直注他聚合 Repository。</p>
 * <p>金额口径：正向结算累加、同窗撤销抵减（一收一退净额为 0）；笔数口径：正向与撤销各计 1 笔（一收一退计 2）。</p>
 */
@Service
@RequiredArgsConstructor
public class YbReconciliationQueryService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 住院预结算表无 {@code clr_type} 列时，按平台住院清算类别归组。
     */
    private static final String CLR_TYPE_INPATIENT = "21";

    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbSettlementReversalQueryService settlementReversalQueryService;
    private final YbInpatientPreSettleQueryService inpatientPreSettleQueryService;

    /**
     * 按险种 + 清算类别汇总指定日期区间内门诊与住院结算快照金额（HIS 本地只读）。
     * <p>供日对账列表「待对账金额」、月清算预览、以及 3201/3202 出站前金额缺省补齐。
     * 本方法本身不调医保；命令中 {@code insutype}/{@code clrType} 非空时仅返回匹配维度行。</p>
     * <p>一收一退：原结算与撤销同窗时金额正负抵净不入净额，笔数仍计两笔。</p>
     *
     * @param command  契约、日期区间及可选维度过滤
     * @param tenantId 租户
     * @return 分组汇总行（按险种、清算类别排序）；无结算时为空列表
     */
    public List<ClearAmountSummaryResult> summarizeClearAmounts(
            SummarizeClearAmountCommand command, Long tenantId) {
        // Pre：解析日期区间（左闭右开）
        DateRange range = resolveDateRange(command);
        SummarizeClearAmountCommand safe = safeCommand(command);
        Map<DimKey, AmountBucket> buckets = new HashMap<>();

        // Cmd：门诊/住院正式结算快照正向累加（含日后撤销的 REVOKED 行，金额由同窗撤销抵减）
        for (YbSettlementRecord row : settlementRecordQueryService.listCreatedBetween(
            tenantId, range.beginAt(), range.endExclusive())) {
            DimKey key = new DimKey(normalizeDim(row.getInsutype()), normalizeDim(row.getClrType()));
            // 可选过滤：仅保留请求指定的险种/清算类别
            if (!matchOptionalFilter(key, safe)) {
                continue;
            }
            AmountBucket bucket = buckets.computeIfAbsent(key, ignored -> new AmountBucket());
            bucket.add(
                nullToZero(row.getMedfeeSumamt()),
                nullToZero(row.getFundPaySumamt()),
                nullToZero(row.getAcctPay()),
                nullToZero(row.getPsnCashPay()));
        }

        // Cmd：同窗撤销凭证按原结算险种/清算类别抵减金额，并各计 1 笔
        applyReversalsInRange(buckets, safe, tenantId, range.beginAt(), range.endExclusive());

        // Cmd：住院已结算预结算无 clr_type，固定归入住院清算类别
        for (YbInpatientPreSettle row : inpatientPreSettleQueryService.listSettledBetween(
            tenantId, range.beginAt(), range.endExclusive())) {
            DimKey key = new DimKey(normalizeDim(row.getInsutype()), CLR_TYPE_INPATIENT);
            if (!matchOptionalFilter(key, safe)) {
                continue;
            }
            AmountBucket bucket = buckets.computeIfAbsent(key, ignored -> new AmountBucket());
            bucket.add(
                nullToZero(row.getMedfeeSumamt()),
                nullToZero(row.getFundPaySumamt()),
                nullToZero(row.getAcctPay()),
                nullToZero(row.getPsnCashPay()));
        }

        // Asm：Map 转有序列表出参
        List<ClearAmountSummaryResult> result = new ArrayList<>(buckets.size());
        List<Map.Entry<DimKey, AmountBucket>> entries = new ArrayList<>(buckets.entrySet());
        entries.sort(Comparator
            .comparing((Map.Entry<DimKey, AmountBucket> e) -> e.getKey().insutype())
            .thenComparing(e -> e.getKey().clrType()));
        for (Map.Entry<DimKey, AmountBucket> entry : entries) {
            DimKey key = entry.getKey();
            AmountBucket bucket = entry.getValue();
            // 仅撤销、无正向时也可能有桶；零金额零笔数仍返回以便 UI/3201 维度对齐
            result.add(new ClearAmountSummaryResult()
                .setInsutype(key.insutype())
                .setClrType(key.clrType())
                .setMedfeeSumamt(bucket.medfee)
                .setMedSumfee(bucket.medfee)
                .setFundAppySum(bucket.fund)
                .setAcctPay(bucket.acct)
                .setCashPayamt(bucket.cash)
                .setSetlCount(bucket.count));
        }
        return result;
    }

    /**
     * 将多行分组汇总折叠为一条区间合计（月清算表单、3202 总额缺省补齐）。
     *
     * @param rows {@link #summarizeClearAmounts} 出参；null 视为空
     * @return 合计行（维度字段为空串）；无行时金额与笔数为 0
     */
    public ClearAmountSummaryResult mergeClearAmountSummaries(List<ClearAmountSummaryResult> rows) {
        BigDecimal medfee = BigDecimal.ZERO;
        BigDecimal fund = BigDecimal.ZERO;
        BigDecimal acct = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        int count = 0;
        if (rows != null) {
            for (ClearAmountSummaryResult row : rows) {
                // 空元素跳过，避免 NPE
                if (row == null) {
                    continue;
                }
                medfee = medfee.add(nullToZero(row.getMedfeeSumamt()));
                fund = fund.add(nullToZero(row.getFundAppySum()));
                acct = acct.add(nullToZero(row.getAcctPay()));
                cash = cash.add(nullToZero(row.getCashPayamt()));
                if (row.getSetlCount() != null) {
                    count += row.getSetlCount();
                }
            }
        }
        return new ClearAmountSummaryResult()
            .setInsutype("")
            .setClrType("")
            .setMedfeeSumamt(medfee)
            .setMedSumfee(medfee)
            .setFundAppySum(fund)
            .setAcctPay(acct)
            .setCashPayamt(cash)
            .setSetlCount(count);
    }

    /**
     * 在分组列表中查找指定险种 + 清算类别行；找不到返回金额/笔数均为 0 的空行。
     *
     * @param rows     分组汇总
     * @param insutype 险种
     * @param clrType  清算类别
     * @return 匹配行或零值行（维度已填入入参归一化值）
     */
    public ClearAmountSummaryResult findClearAmountSummary(
            List<ClearAmountSummaryResult> rows, String insutype, String clrType) {
        String wantInsu = normalizeDim(insutype);
        String wantClr = normalizeDim(clrType);
        if (rows != null) {
            for (ClearAmountSummaryResult row : rows) {
                if (row == null) {
                    continue;
                }
                if (wantInsu.equals(normalizeDim(row.getInsutype()))
                    && wantClr.equals(normalizeDim(row.getClrType()))) {
                    return row;
                }
            }
        }
        return new ClearAmountSummaryResult()
            .setInsutype(wantInsu)
            .setClrType(wantClr)
            .setMedfeeSumamt(BigDecimal.ZERO)
            .setMedSumfee(BigDecimal.ZERO)
            .setFundAppySum(BigDecimal.ZERO)
            .setAcctPay(BigDecimal.ZERO)
            .setCashPayamt(BigDecimal.ZERO)
            .setSetlCount(0);
    }

    /**
     * 加载 3202 明细对账输入文件行预览（门诊 + 住院结算快照 + 同窗撤销反向行）。
     * <p>退费须同时上报正向与反向两条（平台 {@code refd_setl_flag}）；反向行来自撤销凭证。</p>
     *
     * @param command  契约与日期区间
     * @param tenantId 租户
     * @return 3202 输入文件行列表
     */
    public List<ReconDetailRowResult> loadDetailRows(SummarizeClearAmountCommand command, Long tenantId) {
        DateRange range = resolveDateRange(command);
        List<YbSettlementRecord> settlements = settlementRecordQueryService.listCreatedBetween(
            tenantId, range.beginAt(), range.endExclusive());
        List<YbSettlementReversal> reversals = settlementReversalQueryService.listReversedBetween(
            tenantId, range.beginAt(), range.endExclusive());
        // Pre：跨日退费时原结算可能不在本窗，按撤销关联 ID 补装，供反向行补全 setl/mdtrt
        Set<Long> reversalOriginIds = new HashSet<>();
        for (YbSettlementReversal reversal : reversals) {
            if (reversal.getSettlementRecordId() != null) {
                reversalOriginIds.add(reversal.getSettlementRecordId());
            }
        }
        Map<Long, YbSettlementRecord> reversalOrigins =
            settlementRecordQueryService.mapByIds(reversalOriginIds, tenantId);
        List<YbSettlementRecord> reversalOriginRows = new ArrayList<>(reversalOrigins.values());
        List<ReconDetailRowResult> rows = new ArrayList<>();
        rows.addAll(ReconDetailRowSupport.fromOutpatientRows(settlements));
        rows.addAll(ReconDetailRowSupport.fromInpatientRows(
            inpatientPreSettleQueryService.listSettledBetween(tenantId, range.beginAt(), range.endExclusive())));
        rows.addAll(ReconDetailRowSupport.fromReversalRows(reversals, reversalOriginRows));
        return rows;
    }

    /**
     * 将撤销时间窗内的反向凭证按原结算维度抵减金额并计入笔数。
     *
     * @param buckets  汇总桶
     * @param safe     已安全化命令（含可选维度过滤）
     * @param tenantId 租户
     * @param beginAt  起始（含）
     * @param endExclusive 结束（不含）
     */
    private void applyReversalsInRange(
            Map<DimKey, AmountBucket> buckets,
            SummarizeClearAmountCommand safe,
            Long tenantId,
            LocalDateTime beginAt,
            LocalDateTime endExclusive) {
        List<YbSettlementReversal> reversals =
            settlementReversalQueryService.listReversedBetween(tenantId, beginAt, endExclusive);
        if (reversals.isEmpty()) {
            return;
        }
        // Pre：批量回查原结算，取得险种/清算类别（撤销表无此两维）
        Set<Long> settlementIds = new HashSet<>();
        for (YbSettlementReversal reversal : reversals) {
            if (reversal.getSettlementRecordId() != null) {
                settlementIds.add(reversal.getSettlementRecordId());
            }
        }
        Map<Long, YbSettlementRecord> settlementById =
            settlementRecordQueryService.mapByIds(settlementIds, tenantId);
        for (YbSettlementReversal reversal : reversals) {
            YbSettlementRecord origin = settlementById.get(reversal.getSettlementRecordId());
            // 原结算缺失时无法分桶，跳过该反向笔（避免误入空维度）
            if (origin == null) {
                continue;
            }
            DimKey key = new DimKey(normalizeDim(origin.getInsutype()), normalizeDim(origin.getClrType()));
            if (!matchOptionalFilter(key, safe)) {
                continue;
            }
            AmountBucket bucket = buckets.computeIfAbsent(key, ignored -> new AmountBucket());
            // 金额取绝对值再抵减：撤销落库一般为正数；若平台回负号亦不重复取负
            bucket.subtract(
                nullToZero(reversal.getMedfeeSumamt()).abs(),
                nullToZero(reversal.getFundPaySumamt()).abs(),
                nullToZero(reversal.getAcctPay()).abs(),
                nullToZero(reversal.getPsnCashPay()).abs());
        }
    }

    /**
     * 解析并校验汇总/预览请求的日期区间。
     *
     * @param command 含 begndate / enddate 的命令
     * @return 左闭右开时间区间
     */
    private DateRange resolveDateRange(SummarizeClearAmountCommand command) {
        SummarizeClearAmountCommand safe = safeCommand(command);
        LocalDate begin = parseDate(safe.getBegndate(), "开始日期");
        LocalDate end = parseDate(safe.getEnddate(), "结束日期");
        // 结束早于开始则无法形成有效对账窗口
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束日期不能早于开始日期");
        }
        return new DateRange(begin.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    /**
     * 命令空引用时使用空命令，避免 NPE。
     *
     * @param command 原始命令
     * @return 非 null 命令
     */
    private static SummarizeClearAmountCommand safeCommand(SummarizeClearAmountCommand command) {
        if (command == null) {
            return new SummarizeClearAmountCommand();
        }
        return command;
    }

    /**
     * 可选维度过滤：命令未指定则放行；指定则须与分桶键一致。
     *
     * @param key  当前分桶键
     * @param safe 已安全化的命令
     * @return true 表示纳入汇总
     */
    private static boolean matchOptionalFilter(DimKey key, SummarizeClearAmountCommand safe) {
        if (!isEmpty(safe.getInsutype()) && !normalizeDim(safe.getInsutype()).equals(key.insutype())) {
            return false;
        }
        if (!isEmpty(safe.getClrType()) && !normalizeDim(safe.getClrType()).equals(key.clrType())) {
            return false;
        }
        return true;
    }

    /**
     * 维度字段归一化：空白视为空串，便于分组与匹配。
     *
     * @param value 原始维度
     * @return trim 后文本或空串
     */
    private static String normalizeDim(String value) {
        if (isEmpty(value)) {
            return "";
        }
        return value.trim();
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

    /** 汇总分组键：险种 + 清算类别 */
    private record DimKey(String insutype, String clrType) {
    }

    /**
     * 分组累加桶。
     * <p>{@link #add} 正向入账；{@link #subtract} 撤销抵减金额但仍 {@code count+1}（一收一退计两笔）。</p>
     */
    private static final class AmountBucket {
        private BigDecimal medfee = BigDecimal.ZERO;
        private BigDecimal fund = BigDecimal.ZERO;
        private BigDecimal acct = BigDecimal.ZERO;
        private BigDecimal cash = BigDecimal.ZERO;
        private int count;

        /**
         * 正向结算入账：四项金额累加，笔数 +1。
         *
         * @param medfeeAmt 医疗费总额
         * @param fundAmt   基金支付
         * @param acctAmt   个账支付
         * @param cashAmt   现金支付
         */
        private void add(BigDecimal medfeeAmt, BigDecimal fundAmt, BigDecimal acctAmt, BigDecimal cashAmt) {
            medfee = medfee.add(medfeeAmt);
            fund = fund.add(fundAmt);
            acct = acct.add(acctAmt);
            cash = cash.add(cashAmt);
            count += 1;
        }

        /**
         * 撤销反向抵减：四项金额减去入参（应为非负绝对值），笔数仍 +1。
         *
         * @param medfeeAmt 医疗费总额（绝对值）
         * @param fundAmt   基金支付（绝对值）
         * @param acctAmt   个账支付（绝对值）
         * @param cashAmt   现金支付（绝对值）
         */
        private void subtract(BigDecimal medfeeAmt, BigDecimal fundAmt, BigDecimal acctAmt, BigDecimal cashAmt) {
            medfee = medfee.subtract(medfeeAmt);
            fund = fund.subtract(fundAmt);
            acct = acct.subtract(acctAmt);
            cash = cash.subtract(cashAmt);
            count += 1;
        }
    }
}
