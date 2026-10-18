/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.recon.service;

import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.zxplat.plugin.wi.domain.entity.YbWiEncounterAnchor;
import com.zxplat.plugin.wi.domain.loader.WorkInjuryPatientNameLookup;
import com.zxplat.plugin.wi.domain.loader.WorkInjuryPaymentAmountLookup;
import com.zxplat.plugin.wi.domain.recon.result.WorkInjuryPaymentAmountSnapshot;
import com.zxplat.plugin.wi.domain.recon.result.WorkInjuryReconDaySummary;
import com.zxplat.plugin.wi.domain.recon.result.WorkInjuryReconSettlementRow;
import com.zxplat.plugin.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.zxplat.plugin.wi.domain.share.WorkInjuryConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 工伤对账读侧：settlement ↔ payment 勾稽。 */
@Service
@RequiredArgsConstructor
public class WorkInjuryReconQueryService {

    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbWiEncounterAnchorRepository encounterAnchorRepository;
    private final ObjectProvider<WorkInjuryPatientNameLookup> patientNameLookupProvider;
    private final ObjectProvider<WorkInjuryPaymentAmountLookup> paymentAmountLookupProvider;

    /**
     * 按结算日列出工伤正式结算（含已撤销），并附收银明细金额。
     */
    public List<WorkInjuryReconSettlementRow> listSettlementsByDay(
            Long contractId, LocalDate day, Long tenantId) {
        if (contractId == null || day == null || tenantId == null) {
            return List.of();
        }
        List<YbSettlementRecord> records = loadWorkInjuryRecords(
                contractId, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), tenantId);
        Map<Long, WorkInjuryPaymentAmountSnapshot> paymentMap = loadPaymentSnapshots(records);
        Map<Long, String> iptOtpNoByEncounter = loadIptOtpNoByEncounter(records);
        List<WorkInjuryReconSettlementRow> rows = new ArrayList<>(records.size());
        for (YbSettlementRecord record : records) {
            rows.add(toRow(record, paymentMap, iptOtpNoByEncounter));
        }
        rows.sort(Comparator
                .comparing(WorkInjuryReconSettlementRow::getSetlTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(row -> Optional.ofNullable(row.getSettlementRecordId()).orElse(0L)));
        return rows;
    }

    /**
     * 按日汇总 settlement ↔ payment（左闭右闭日期区间）。
     * <p>不查流水号/姓名，避免整月 N+1。</p>
     */
    public List<WorkInjuryReconDaySummary> summarizeByDayRange(
            Long contractId, LocalDate beginDay, LocalDate endDay, Long tenantId) {
        if (contractId == null || beginDay == null || endDay == null || tenantId == null) {
            return List.of();
        }
        if (endDay.isBefore(beginDay)) {
            return List.of();
        }
        List<YbSettlementRecord> records = loadWorkInjuryRecords(
                contractId, beginDay.atStartOfDay(), endDay.plusDays(1).atStartOfDay(), tenantId);
        Map<Long, WorkInjuryPaymentAmountSnapshot> paymentMap = loadPaymentSnapshots(records);
        Map<LocalDate, DayBucket> buckets = new LinkedHashMap<>();
        for (LocalDate cursor = beginDay; !cursor.isAfter(endDay); cursor = cursor.plusDays(1)) {
            buckets.put(cursor, new DayBucket());
        }
        for (YbSettlementRecord record : records) {
            LocalDate day = resolveReconDay(record);
            if (day == null) {
                continue;
            }
            DayBucket bucket = buckets.get(day);
            if (bucket == null) {
                continue;
            }
            bucket.add(toSummaryRow(record, paymentMap));
        }
        List<WorkInjuryReconDaySummary> result = new ArrayList<>(buckets.size());
        for (Map.Entry<LocalDate, DayBucket> entry : buckets.entrySet()) {
            result.add(entry.getValue().toSummary(entry.getKey()));
        }
        return result;
    }

    private List<YbSettlementRecord> loadWorkInjuryRecords(
            Long contractId, LocalDateTime beginAt, LocalDateTime endExclusive, Long tenantId) {
        return settlementRecordQueryService.listBySetlTimeBetweenAndContractAndSettleMode(
                tenantId, contractId, WorkInjuryConstants.SCHEME_CODE, beginAt, endExclusive);
    }

    private Map<Long, WorkInjuryPaymentAmountSnapshot> loadPaymentSnapshots(List<YbSettlementRecord> records) {
        WorkInjuryPaymentAmountLookup lookup = paymentAmountLookupProvider.getIfAvailable();
        if (lookup == null || records == null || records.isEmpty()) {
            return Map.of();
        }
        Set<Long> paymentIds = new HashSet<>();
        for (YbSettlementRecord record : records) {
            if (record != null && record.getPaymentId() != null) {
                paymentIds.add(record.getPaymentId());
            }
        }
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, WorkInjuryPaymentAmountSnapshot> loaded = lookup.findByPaymentIds(paymentIds);
        return loaded == null ? Map.of() : loaded;
    }

    private Map<Long, String> loadIptOtpNoByEncounter(List<YbSettlementRecord> records) {
        Set<Long> encounterIds = new HashSet<>();
        for (YbSettlementRecord record : records) {
            if (record != null && record.getEncounterId() != null) {
                encounterIds.add(record.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return Map.of();
        }
        List<YbWiEncounterAnchor> anchors = encounterAnchorRepository.findByEncounterIdIn(encounterIds);
        if (anchors == null || anchors.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>(anchors.size());
        for (YbWiEncounterAnchor anchor : anchors) {
            if (anchor == null || anchor.getEncounterId() == null) {
                continue;
            }
            if (StringUtils.hasText(anchor.getIptOtpNo())) {
                map.put(anchor.getEncounterId(), anchor.getIptOtpNo().trim());
            }
        }
        return map;
    }

    /** 日汇总用轻量行：仅金额勾稽字段，不查流水号/姓名。 */
    private WorkInjuryReconSettlementRow toSummaryRow(
            YbSettlementRecord record, Map<Long, WorkInjuryPaymentAmountSnapshot> paymentMap) {
        WorkInjuryReconSettlementRow row = new WorkInjuryReconSettlementRow()
                .setSettlementRecordId(record.getId())
                .setEncounterId(record.getEncounterId())
                .setMedfeeSumamt(record.getMedfeeSumamt())
                .setFundPaySumamt(record.getFundPaySumamt())
                .setPsnCashPay(record.getPsnCashPay())
                .setStatus(record.getStatus() == null ? null : String.valueOf(record.getStatus()))
                .setPaymentId(record.getPaymentId());
        applyPayment(row, record.getPaymentId(), paymentMap);
        return row;
    }

    private WorkInjuryReconSettlementRow toRow(
            YbSettlementRecord record,
            Map<Long, WorkInjuryPaymentAmountSnapshot> paymentMap,
            Map<Long, String> iptOtpNoByEncounter) {
        String iptOtpNo = record.getEncounterId() == null
                ? null
                : iptOtpNoByEncounter.get(record.getEncounterId());
        String psnName = StringUtils.hasText(record.getPsnName()) ? record.getPsnName().trim() : null;
        if (!StringUtils.hasText(psnName)) {
            WorkInjuryPatientNameLookup nameLookup = patientNameLookupProvider.getIfAvailable();
            if (nameLookup != null) {
                psnName = nameLookup.findNameByEncounterId(record.getEncounterId());
            }
        }
        WorkInjuryReconSettlementRow row = new WorkInjuryReconSettlementRow()
                .setSettlementRecordId(record.getId())
                .setEncounterId(record.getEncounterId())
                .setIptOtpNo(iptOtpNo)
                .setMdtrtId(record.getMdtrtId())
                .setSetlId(record.getSetlId())
                .setPsnName(psnName)
                .setSetlTime(record.getSetlTime())
                .setMedfeeSumamt(record.getMedfeeSumamt())
                .setFundPaySumamt(record.getFundPaySumamt())
                .setPsnCashPay(record.getPsnCashPay())
                .setMsgid2207(record.getMsgid2207())
                .setStatus(record.getStatus() == null ? null : String.valueOf(record.getStatus()))
                .setPaymentId(record.getPaymentId());
        applyPayment(row, record.getPaymentId(), paymentMap);
        return row;
    }

    private static void applyPayment(
            WorkInjuryReconSettlementRow row,
            Long paymentId,
            Map<Long, WorkInjuryPaymentAmountSnapshot> paymentMap) {
        if (paymentId == null || paymentMap == null) {
            return;
        }
        WorkInjuryPaymentAmountSnapshot snap = paymentMap.get(paymentId);
        if (snap == null) {
            return;
        }
        row.setPaymentStatus(snap.getPaymentStatus())
                .setPaymentMedfee(snap.getPaymentMedfee())
                .setPaymentHifp(snap.getPaymentHifp())
                .setPaymentCash(snap.getPaymentCash());
    }

    private static LocalDate resolveReconDay(YbSettlementRecord record) {
        if (record == null) {
            return null;
        }
        LocalDateTime at = record.getSetlTime() != null ? record.getSetlTime() : record.getCreatedAt();
        return at == null ? null : at.toLocalDate();
    }

    static boolean isRevoked(WorkInjuryReconSettlementRow row) {
        return row != null && "REVOKED".equalsIgnoreCase(String.valueOf(row.getStatus()));
    }

    static boolean isAmountBalanced(WorkInjuryReconSettlementRow row) {
        if (row == null || row.getPaymentId() == null) {
            return false;
        }
        if (row.getPaymentMedfee() == null || row.getPaymentHifp() == null || row.getPaymentCash() == null) {
            return false;
        }
        return amountDiff(row.getMedfeeSumamt(), row.getPaymentMedfee()) == 0
                && amountDiff(row.getFundPaySumamt(), row.getPaymentHifp()) == 0
                && amountDiff(row.getPsnCashPay(), row.getPaymentCash()) == 0;
    }

    private static int amountDiff(BigDecimal left, BigDecimal right) {
        BigDecimal a = left == null ? BigDecimal.ZERO : left;
        BigDecimal b = right == null ? BigDecimal.ZERO : right;
        return a.subtract(b).setScale(2, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class DayBucket {
        private BigDecimal medfee = BigDecimal.ZERO;
        private BigDecimal fund = BigDecimal.ZERO;
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal paymentMedfee = BigDecimal.ZERO;
        private BigDecimal paymentHifp = BigDecimal.ZERO;
        private BigDecimal paymentCash = BigDecimal.ZERO;
        private int setlCount;
        private int balancedCount;
        private int unbalancedCount;

        void add(WorkInjuryReconSettlementRow row) {
            if (row == null) {
                return;
            }
            // 已撤销不计入日汇总金额/笔数（明细仍展示）
            if (isRevoked(row)) {
                return;
            }
            setlCount += 1;
            medfee = medfee.add(nz(row.getMedfeeSumamt()));
            fund = fund.add(nz(row.getFundPaySumamt()));
            cash = cash.add(nz(row.getPsnCashPay()));
            paymentMedfee = paymentMedfee.add(nz(row.getPaymentMedfee()));
            paymentHifp = paymentHifp.add(nz(row.getPaymentHifp()));
            paymentCash = paymentCash.add(nz(row.getPaymentCash()));
            if (isAmountBalanced(row)) {
                balancedCount += 1;
            } else {
                unbalancedCount += 1;
            }
        }

        WorkInjuryReconDaySummary toSummary(LocalDate day) {
            return new WorkInjuryReconDaySummary()
                    .setDay(day)
                    .setMedfeeSumamt(medfee.setScale(2, RoundingMode.HALF_UP))
                    .setFundPaySumamt(fund.setScale(2, RoundingMode.HALF_UP))
                    .setPsnCashPay(cash.setScale(2, RoundingMode.HALF_UP))
                    .setPaymentMedfee(paymentMedfee.setScale(2, RoundingMode.HALF_UP))
                    .setPaymentHifp(paymentHifp.setScale(2, RoundingMode.HALF_UP))
                    .setPaymentCash(paymentCash.setScale(2, RoundingMode.HALF_UP))
                    .setSetlCount(setlCount)
                    .setBalancedCount(balancedCount)
                    .setUnbalancedCount(unbalancedCount);
        }
    }
}
