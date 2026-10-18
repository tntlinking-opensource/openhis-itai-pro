/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.application.recon.service;

import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.wi.application.recon.request.ConfirmWorkInjuryReconDailyRequest;
import com.zxplat.plugin.wi.application.recon.request.ListWorkInjuryReconSettlementsRequest;
import com.zxplat.plugin.wi.application.recon.request.SaveWorkInjuryReconDailyPlatformRequest;
import com.zxplat.plugin.wi.application.recon.request.SummarizeWorkInjuryReconByDayRangeRequest;
import com.zxplat.plugin.wi.application.recon.response.WorkInjuryReconDaySummaryResponse;
import com.zxplat.plugin.wi.application.recon.response.WorkInjuryReconSettlementResponse;
import com.zxplat.plugin.wi.domain.recon.result.WorkInjuryReconDaySummary;
import com.zxplat.plugin.wi.domain.recon.result.WorkInjuryReconSettlementRow;
import com.zxplat.plugin.wi.domain.recon.service.WorkInjuryReconDailyDomainService;
import com.zxplat.plugin.wi.domain.recon.service.WorkInjuryReconQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 工伤对账读侧 UI（settlement ↔ payment）+ 日结果持久化。 */
@Service
@RequiredArgsConstructor
public class WorkInjuryReconAppService {

    private final WorkInjuryReconQueryService workInjuryReconQueryService;
    private final WorkInjuryReconDailyDomainService workInjuryReconDailyDomainService;
    private final ChsInsuranceContractReader contractReader;

    /** 按日列出 WORK_INJURY 结算明细（含收银勾稽金额）。 */
    public List<WorkInjuryReconSettlementResponse> listSettlementsByDay(
            ListWorkInjuryReconSettlementsRequest request) {
        if (request == null || !StringUtils.hasText(request.getDay())) {
            return List.of();
        }
        ChsContractSnapshot contract = resolveContract(request.getContractId(), request.getContractBusNo());
        if (contract == null) {
            return List.of();
        }
        LocalDate day = parseDay(request.getDay());
        if (day == null) {
            return List.of();
        }
        return workInjuryReconQueryService
                .listSettlementsByDay(contract.id(), day, TenantUtils.getCurrentTenantId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 按日汇总 settlement ↔ payment，并 upsert 日结果（回填确认/平台列）。 */
    public List<WorkInjuryReconDaySummaryResponse> summarizeByDayRange(
            SummarizeWorkInjuryReconByDayRangeRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getBegndate())
                || !StringUtils.hasText(request.getEnddate())) {
            return List.of();
        }
        ChsContractSnapshot contract = resolveContract(request.getContractId(), request.getContractBusNo());
        if (contract == null) {
            return List.of();
        }
        LocalDate begin = parseDay(request.getBegndate());
        LocalDate end = parseDay(request.getEnddate());
        if (begin == null || end == null) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<WorkInjuryReconDaySummary> live = workInjuryReconQueryService.summarizeByDayRange(
                contract.id(), begin, end, tenantId);
        return workInjuryReconDailyDomainService
                .upsertAndMerge(contract.id(), contract.busNo(), live, tenantId)
                .stream()
                .map(this::toDayResponse)
                .toList();
    }

    /** 确认某日账平。 */
    public void confirmDailyBalanced(ConfirmWorkInjuryReconDailyRequest request) {
        if (request == null || !StringUtils.hasText(request.getDay())) {
            throw new BusinessException("WI-VAL-001", "day 不能为空");
        }
        ChsContractSnapshot contract = requireContract(request.getContractId(), request.getContractBusNo());
        LocalDate day = parseDay(request.getDay());
        if (day == null) {
            throw new BusinessException("WI-VAL-001", "day 格式须为 yyyy-MM-dd");
        }
        workInjuryReconDailyDomainService.confirmBalanced(
                contract.id(), contract.busNo(), day, TenantUtils.getCurrentTenantId());
    }

    /** 保存某日平台 1320 金额。 */
    public void saveDailyPlatform(SaveWorkInjuryReconDailyPlatformRequest request) {
        if (request == null || !StringUtils.hasText(request.getDay())) {
            throw new BusinessException("WI-VAL-001", "day 不能为空");
        }
        ChsContractSnapshot contract = requireContract(request.getContractId(), request.getContractBusNo());
        LocalDate day = parseDay(request.getDay());
        if (day == null) {
            throw new BusinessException("WI-VAL-001", "day 格式须为 yyyy-MM-dd");
        }
        workInjuryReconDailyDomainService.savePlatform(
                contract.id(),
                contract.busNo(),
                day,
                request.getPlatformMedfee(),
                request.getPlatformHifp(),
                request.getPlatformCash(),
                request.getReconScope(),
                TenantUtils.getCurrentTenantId());
    }

    private ChsContractSnapshot resolveContract(Long contractId, String contractBusNo) {
        if (contractId == null && !StringUtils.hasText(contractBusNo)) {
            return null;
        }
        return contractReader.requireContract(
                StringUtils.hasText(contractBusNo) ? contractBusNo.trim() : null, contractId);
    }

    private ChsContractSnapshot requireContract(Long contractId, String contractBusNo) {
        ChsContractSnapshot contract = resolveContract(contractId, contractBusNo);
        if (contract == null) {
            throw new BusinessException("WI-VAL-001", "contractId 或 contractBusNo 不能为空");
        }
        return contract;
    }

    private static LocalDate parseDay(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException | NullPointerException ex) {
            return null;
        }
    }

    private WorkInjuryReconSettlementResponse toResponse(WorkInjuryReconSettlementRow row) {
        boolean posted2207 = StringUtils.hasText(row.getMsgid2207())
                && "CONFIRMED".equalsIgnoreCase(String.valueOf(row.getStatus()));
        return new WorkInjuryReconSettlementResponse()
                .setSettlementRecordId(row.getSettlementRecordId())
                .setEncounterId(row.getEncounterId())
                .setIptOtpNo(row.getIptOtpNo())
                .setMdtrtId(row.getMdtrtId())
                .setSetlId(row.getSetlId())
                .setPsnName(row.getPsnName())
                .setSetlTime(row.getSetlTime())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundPaySumamt(row.getFundPaySumamt())
                .setPsnCashPay(row.getPsnCashPay())
                .setMsgid2207(row.getMsgid2207())
                .setStatus(row.getStatus())
                .setPosted2207(posted2207)
                .setPaymentId(row.getPaymentId())
                .setPaymentStatus(row.getPaymentStatus())
                .setPaymentMedfee(row.getPaymentMedfee())
                .setPaymentHifp(row.getPaymentHifp())
                .setPaymentCash(row.getPaymentCash());
    }

    private WorkInjuryReconDaySummaryResponse toDayResponse(WorkInjuryReconDaySummary row) {
        return new WorkInjuryReconDaySummaryResponse()
                .setDay(row.getDay() == null ? null : row.getDay().toString())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundPaySumamt(row.getFundPaySumamt())
                .setPsnCashPay(row.getPsnCashPay())
                .setPaymentMedfee(row.getPaymentMedfee())
                .setPaymentHifp(row.getPaymentHifp())
                .setPaymentCash(row.getPaymentCash())
                .setSetlCount(row.getSetlCount())
                .setBalancedCount(row.getBalancedCount())
                .setUnbalancedCount(row.getUnbalancedCount())
                .setLocalStatus(row.getLocalStatus())
                .setConfirmed(row.getConfirmed())
                .setPlatformMedfee(row.getPlatformMedfee())
                .setPlatformHifp(row.getPlatformHifp())
                .setPlatformCash(row.getPlatformCash())
                .setReconScope(row.getReconScope());
    }
}
