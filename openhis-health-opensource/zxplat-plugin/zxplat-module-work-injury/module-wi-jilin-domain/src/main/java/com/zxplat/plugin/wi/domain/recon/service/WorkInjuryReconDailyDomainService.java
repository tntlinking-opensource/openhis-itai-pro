/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.recon.service;

import com.zxplat.plugin.wi.domain.entity.YbWiReconDaily;
import com.zxplat.plugin.wi.domain.recon.result.WorkInjuryReconDaySummary;
import com.zxplat.plugin.wi.domain.repository.YbWiReconDailyRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工伤对账日结果写侧：结算统计 upsert、确认账平、保存 1320 平台金额。
 */
@Service
@RequiredArgsConstructor
public class WorkInjuryReconDailyDomainService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_BALANCED = "balanced";
    public static final String STATUS_UNBALANCED = "unbalanced";

    private final YbWiReconDailyRepository reconDailyRepository;

    /**
     * 按实时日汇总 upsert 金额快照，并回填 confirmed / platform* / localStatus。
     */
    @Transactional
    public List<WorkInjuryReconDaySummary> upsertAndMerge(
            Long contractId,
            String contractBusNo,
            List<WorkInjuryReconDaySummary> liveSummaries,
            Long tenantId) {
        if (contractId == null || tenantId == null || liveSummaries == null || liveSummaries.isEmpty()) {
            return liveSummaries == null ? List.of() : liveSummaries;
        }
        String busNo = StringUtils.hasText(contractBusNo) ? contractBusNo.trim() : "";
        List<LocalDate> days = new ArrayList<>(liveSummaries.size());
        for (WorkInjuryReconDaySummary summary : liveSummaries) {
            if (summary != null && summary.getDay() != null) {
                days.add(summary.getDay());
            }
        }
        Map<LocalDate, YbWiReconDaily> existingByDay = loadByDays(contractId, tenantId, days);
        List<WorkInjuryReconDaySummary> merged = new ArrayList<>(liveSummaries.size());
        for (WorkInjuryReconDaySummary live : liveSummaries) {
            if (live == null || live.getDay() == null) {
                continue;
            }
            YbWiReconDaily row = existingByDay.get(live.getDay());
            if (row == null) {
                row = newRow(contractId, busNo, live.getDay(), tenantId);
            } else if (StringUtils.hasText(busNo)) {
                row.setContractBusNo(busNo);
            }
            applyLiveAmounts(row, live);
            reconDailyRepository.save(row);
            merged.add(mergeOverlay(live, row));
        }
        return merged;
    }

    /** 用户确认当日账平。 */
    @Transactional
    public YbWiReconDaily confirmBalanced(
            Long contractId, String contractBusNo, LocalDate day, Long tenantId) {
        requireKeys(contractId, day, tenantId);
        YbWiReconDaily row = loadOrCreate(contractId, contractBusNo, day, tenantId);
        row.setConfirmed(true);
        row.setLocalStatus(STATUS_BALANCED);
        return reconDailyRepository.saveAndFlush(row);
    }

    /** 1320 成功后保存平台金额。 */
    @Transactional
    public YbWiReconDaily savePlatform(
            Long contractId,
            String contractBusNo,
            LocalDate day,
            BigDecimal platformMedfee,
            BigDecimal platformHifp,
            BigDecimal platformCash,
            String reconScope,
            Long tenantId) {
        requireKeys(contractId, day, tenantId);
        YbWiReconDaily row = loadOrCreate(contractId, contractBusNo, day, tenantId);
        row.setPlatformMedfee(platformMedfee);
        row.setPlatformHifp(platformHifp);
        row.setPlatformCash(platformCash);
        if (StringUtils.hasText(reconScope)) {
            row.setReconScope(reconScope.trim());
        }
        return reconDailyRepository.saveAndFlush(row);
    }

    private Map<LocalDate, YbWiReconDaily> loadByDays(
            Long contractId, Long tenantId, List<LocalDate> days) {
        if (days.isEmpty()) {
            return Map.of();
        }
        List<YbWiReconDaily> rows =
                reconDailyRepository.findAllByContractIdAndTenantIdAndIsDeletedFalseAndReconDayIn(
                        contractId, tenantId, days);
        Map<LocalDate, YbWiReconDaily> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (YbWiReconDaily row : rows) {
            if (row != null && row.getReconDay() != null) {
                map.put(row.getReconDay(), row);
            }
        }
        return map;
    }

    private YbWiReconDaily loadOrCreate(
            Long contractId, String contractBusNo, LocalDate day, Long tenantId) {
        Optional<YbWiReconDaily> existing =
                reconDailyRepository.findFirstByContractIdAndReconDayAndTenantIdAndIsDeletedFalse(
                        contractId, day, tenantId);
        if (existing.isPresent()) {
            YbWiReconDaily row = existing.get();
            if (StringUtils.hasText(contractBusNo)) {
                row.setContractBusNo(contractBusNo.trim());
            }
            return row;
        }
        String busNo = StringUtils.hasText(contractBusNo) ? contractBusNo.trim() : "";
        return newRow(contractId, busNo, day, tenantId);
    }

    private static YbWiReconDaily newRow(
            Long contractId, String contractBusNo, LocalDate day, Long tenantId) {
        YbWiReconDaily row = new YbWiReconDaily();
        row.setContractId(contractId);
        row.setContractBusNo(contractBusNo == null ? "" : contractBusNo);
        row.setReconDay(day);
        row.setLocalStatus(STATUS_PENDING);
        row.setConfirmed(false);
        row.setTenantId(tenantId);
        return row;
    }

    private static void applyLiveAmounts(YbWiReconDaily row, WorkInjuryReconDaySummary live) {
        row.setMedfeeSumamt(live.getMedfeeSumamt());
        row.setFundPaySumamt(live.getFundPaySumamt());
        row.setPsnCashPay(live.getPsnCashPay());
        row.setPaymentMedfee(live.getPaymentMedfee());
        row.setPaymentHifp(live.getPaymentHifp());
        row.setPaymentCash(live.getPaymentCash());
        row.setSetlCount(live.getSetlCount());
        row.setUnbalancedCount(live.getUnbalancedCount());

        int unbalanced = live.getUnbalancedCount() == null ? 0 : live.getUnbalancedCount();
        int setlCount = live.getSetlCount() == null ? 0 : live.getSetlCount();
        boolean wasConfirmed = Boolean.TRUE.equals(row.getConfirmed());

        if (unbalanced > 0) {
            row.setConfirmed(false);
            row.setLocalStatus(STATUS_UNBALANCED);
            return;
        }
        if (wasConfirmed) {
            row.setConfirmed(true);
            row.setLocalStatus(STATUS_BALANCED);
            return;
        }
        if (setlCount == 0) {
            row.setConfirmed(false);
            row.setLocalStatus(STATUS_BALANCED);
            return;
        }
        row.setConfirmed(false);
        row.setLocalStatus(STATUS_PENDING);
    }

    private static WorkInjuryReconDaySummary mergeOverlay(
            WorkInjuryReconDaySummary live, YbWiReconDaily row) {
        return live
                .setLocalStatus(row.getLocalStatus())
                .setConfirmed(Boolean.TRUE.equals(row.getConfirmed()))
                .setPlatformMedfee(row.getPlatformMedfee())
                .setPlatformHifp(row.getPlatformHifp())
                .setPlatformCash(row.getPlatformCash())
                .setReconScope(row.getReconScope());
    }

    private static void requireKeys(Long contractId, LocalDate day, Long tenantId) {
        if (contractId == null || day == null || tenantId == null) {
            throw new BusinessException("WI-VAL-001", "contractId / day / tenantId 不能为空");
        }
    }
}
