/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.service;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.reconciliation.command.SummarizeClearAmountCommand;
import com.zxplat.plugin.chs.domain.reconciliation.result.ClearAmountSummaryResult;
import com.zxplat.plugin.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.zxplat.plugin.chs.domain.reconciliation.support.ReconDetailRowSupport;
import com.zxplat.plugin.chs.domain.settlement.YbSettlementVoucherStatus;
import com.zxplat.plugin.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementReversal;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementReversalQueryService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
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
 * <p>数据源仅为本院 HIS 已落库结算回执与撤销凭证（门诊 2207 / 住院 2304 同一张
 * {@code yb_settlement_record}）；<b>不访问医保网关、不发起 3201/3202</b>。
 * 平台对账由 bridge Invoker / Workflow 在用户点击「发起对账」时另行调用。</p>
 * <p>跨聚合读：经 settlement 本模块 QueryService 装载快照，不直注他聚合 Repository。</p>
 * <p>金额口径：正向累加（含已撤销回执）、同窗撤销抵减（一收一退净 0）；笔数：正向与撤销各计 1。
 * 3202 正向仅 CONFIRMED；已撤销只出反向行。若 3201 仍不平，以 3202 差异文件为准继续核对。</p>
 * <p>日期口径：正向按平台 {@code setl_time}（空则回落创建时间）；撤销按 {@code reversed_at}。</p>
 */
@Service
@RequiredArgsConstructor
public class YbReconciliationQueryService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 2304 未回传 {@code clr_type} 时，按平台住院清算类别归组（与门诊已落库 clr_type 对齐）。
     */
    private static final String CLR_TYPE_INPATIENT = "21";

    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbSettlementReversalQueryService settlementReversalQueryService;
    private final ChsInsuranceContractReader contractReader;

    /**
     * 按险种 + 清算类别汇总指定日期区间内门诊与住院结算回执金额（HIS 本地只读）。
     * <p>供日对账列表「待对账金额」、月清算预览、以及 3201/3202 出站前金额缺省补齐。
     * 本方法本身不调医保；命令中 {@code contractBusNo}/{@code contractId} 非空时仅汇总该契约结算；
     * {@code insutype}/{@code clrType} 非空时仅返回匹配维度行。</p>
     * <p>一收一退：原结算与撤销同窗时金额正负抵净；笔数各计 1。
     * 住院与门诊同一数据源，不再叠加预结算快照（叠加会导致金额/笔数翻倍、3202 重复 setl_id）。</p>
     *
     * @param command  契约、日期区间及可选维度过滤
     * @param tenantId 租户
     * @return 分组汇总行（按险种、清算类别排序）；无结算时为空列表
     */
    public List<ClearAmountSummaryResult> summarizeClearAmounts(
            SummarizeClearAmountCommand command, Long tenantId) {
        // Pre：解析日期区间（左闭右开）与可选契约范围（省/市医保须分开汇总）
        DateRange range = resolveDateRange(command);
        SummarizeClearAmountCommand safe = safeCommand(command);
        Long contractIdFilter = resolveContractIdFilter(safe);
        Map<DimKey, AmountBucket> buckets = new HashMap<>();

        List<YbSettlementRecord> loaded =
            settlementRecordQueryService.listBySetlTimeBetween(
                tenantId, range.beginAt(), range.endExclusive());

        // Cmd：按 setl_time 正向累加（含 REVOKED；金额由同窗撤销抵减）
        for (YbSettlementRecord row : loaded) {
            if (!matchContractFilter(row.getContractId(), contractIdFilter)) {
                continue;
            }
            DimKey key = new DimKey(normalizeDim(row.getInsutype()), resolveClrType(row));
            // 可选过滤：仅保留请求指定的险种/清算类别
            if (!matchOptionalFilter(key, safe)) {
                continue;
            }
            AmountBucket bucket = buckets.computeIfAbsent(key, ignored -> new AmountBucket());
            // 金额含 REVOKED（同窗撤销再抵减）；笔数含 REVOKED（一收一退计 2；8/2 实证 61 仅笔数不平→应为 70）
            bucket.add(
                nullToZero(row.getMedfeeSumamt()),
                nullToZero(row.getFundPaySumamt()),
                nullToZero(row.getAcctPay()),
                nullToZero(row.getPsnCashPay()));
        }

        // Cmd：同窗撤销凭证按原结算险种/清算类别抵减金额，并各计 1 笔
        applyReversalsInRange(
            buckets, safe, contractIdFilter, tenantId, range.beginAt(), range.endExclusive());

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
     * 加载 3202 明细对账输入文件行预览（门诊 + 住院正式结算回执 + 同窗撤销反向行）。
     * <p>正向行仅 {@code CONFIRMED}（{@code refd_setl_flag=0}）；已 {@code REVOKED} 不得再以原
     * {@code setl_id} 打正向（中心侧已是退费，再报正向会「退费状态不一致」），只经同窗
     * {@code yb_settlement_reversal} 出反向（{@code revoke_setl_id} + {@code refd_setl_flag=1}）。
     * 指定契约时仅包含该契约结算及其撤销反向行。住院不再叠加预结算快照，避免与 2304 回执重复。</p>
     *
     * @param command  契约与日期区间
     * @param tenantId 租户
     * @return 3202 输入文件行列表
     */
    public List<ReconDetailRowResult> loadDetailRows(SummarizeClearAmountCommand command, Long tenantId) {
        DateRange range = resolveDateRange(command);
        SummarizeClearAmountCommand safe = safeCommand(command);
        Long contractIdFilter = resolveContractIdFilter(safe);
        // Pre：正向仅 CONFIRMED；已撤销由 fromReversalRows 出反向行（revoke_setl_id），禁止再塞回正向
        List<YbSettlementRecord> settlements = new ArrayList<>();
        for (YbSettlementRecord row : settlementRecordQueryService.listBySetlTimeBetween(
            tenantId, range.beginAt(), range.endExclusive())) {
            if (!matchContractFilter(row.getContractId(), contractIdFilter)) {
                continue;
            }
            DimKey key = new DimKey(normalizeDim(row.getInsutype()), resolveClrType(row));
            if (!matchOptionalFilter(key, safe)) {
                continue;
            }
            // 已撤销回执若打 refd_setl_flag=0 会与中心退费态冲突
            if (!YbSettlementVoucherStatus.CONFIRMED.equals(row.getStatus())) {
                continue;
            }
            settlements.add(row);
        }
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
        List<YbSettlementRecord> reversalOriginRows = new ArrayList<>();
        List<YbSettlementReversal> contractReversals = new ArrayList<>();
        for (YbSettlementReversal reversal : reversals) {
            YbSettlementRecord origin = reversalOrigins.get(reversal.getSettlementRecordId());
            // 指定契约时：原结算缺失或不属于本契约则不入明细文件
            if (origin == null || !matchContractFilter(origin.getContractId(), contractIdFilter)) {
                continue;
            }
            DimKey key = new DimKey(normalizeDim(origin.getInsutype()), resolveClrType(origin));
            if (!matchOptionalFilter(key, safe)) {
                continue;
            }
            contractReversals.add(reversal);
            reversalOriginRows.add(origin);
        }
        List<ReconDetailRowResult> rows = new ArrayList<>();
        rows.addAll(ReconDetailRowSupport.fromSettlementRows(settlements));
        rows.addAll(ReconDetailRowSupport.fromReversalRows(contractReversals, reversalOriginRows));
        return rows;
    }

    /**
     * 将撤销时间窗内的反向凭证按原结算维度抵减金额并计入笔数。
     *
     * @param buckets          汇总桶
     * @param safe             已安全化命令（含可选维度过滤）
     * @param contractIdFilter 可选契约主键；null 表示不过滤
     * @param tenantId         租户
     * @param beginAt          起始（含）
     * @param endExclusive     结束（不含）
     */
    private void applyReversalsInRange(
            Map<DimKey, AmountBucket> buckets,
            SummarizeClearAmountCommand safe,
            Long contractIdFilter,
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
            if (!matchContractFilter(origin.getContractId(), contractIdFilter)) {
                continue;
            }
            DimKey key = new DimKey(normalizeDim(origin.getInsutype()), resolveClrType(origin));
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
     * 由 {@code contractBusNo}/{@code contractId} 解析契约主键；二者皆空则不过滤。
     *
     * @param safe 已安全化命令
     * @return 契约主键；未指定契约时为 null
     */
    private Long resolveContractIdFilter(SummarizeClearAmountCommand safe) {
        if (isEmpty(safe.getContractBusNo()) && safe.getContractId() == null) {
            return null;
        }
        ChsContractSnapshot contract =
            contractReader.requireContract(safe.getContractBusNo(), safe.getContractId());
        return contract.id();
    }

    /**
     * 可选契约过滤：未指定契约时放行；指定则须与结算 {@code contractId} 一致。
     *
     * @param rowContractId    结算行契约主键
     * @param contractIdFilter 请求契约主键；null 表示不过滤
     * @return true 表示纳入汇总/明细
     */
    private static boolean matchContractFilter(Long rowContractId, Long contractIdFilter) {
        if (contractIdFilter == null) {
            return true;
        }
        return contractIdFilter.equals(rowContractId);
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
     * 结算回执清算类别：已落库优先；住院缺省时归入 {@code 21}（2304 常不回传 clr_type）。
     *
     * @param row 结算回执
     * @return 归一化清算类别
     */
    private static String resolveClrType(YbSettlementRecord row) {
        String clrType = normalizeDim(row.getClrType());
        if (!clrType.isEmpty()) {
            return clrType;
        }
        if (ChsSettleEncounterClass.IMP.equals(row.getEncounterClass())) {
            return CLR_TYPE_INPATIENT;
        }
        return clrType;
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
