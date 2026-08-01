/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.service;

import com.openhis.health.application.peis.group.dto.GroupRosterRowDto;
import com.openhis.health.application.physicalexam.financial.groupcharge.dto.GroupChargeFailureRowDto;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.ConfirmGroupBatchSettlementRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.PageGroupChargeRosterRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.PreSettleBatchRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.RefreshRosterChargeStatusRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.ConfirmGroupBatchSettlementResponse;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.GroupChargePageInitResponse;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.PreSettleBatchResponse;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.RosterChargeStatusResponse;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 团检批量收费应用服务（AS-PE-FIN-002）。
 *
 * <p>先收费后建单：结算成功后为每人创建 Encounter(PE) 并标记名册 PAID。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class PhysicalExamGroupChargeAppService {

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;
    private final GroupChargeSettlementExecutor groupChargeSettlementExecutor;
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 团检收费页初始化（GCHG-01）。
     */
    public GroupChargePageInitResponse loadPageInit() {
        return new GroupChargePageInitResponse();
    }

    /**
     * 分页查询未收费名册（GCHG-02）。
     */
    public PagedResult<GroupRosterRowDto> pageUnpaidRoster(PageGroupChargeRosterRequest request) {
        return pageRosterByPayStatus(request, PeisGroupRosterConstants.PAY_UNPAID);
    }

    /**
     * 分页查询已收费名册（GCHG-03）。
     */
    public PagedResult<GroupRosterRowDto> pagePaidRoster(PageGroupChargeRosterRequest request) {
        return pageRosterByPayStatus(request, PeisGroupRosterConstants.PAY_PAID);
    }

    /**
     * 批量预结算（GCHG-04）。
     */
    @Transactional(readOnly = true)
    public PreSettleBatchResponse preSettleBatch(PreSettleBatchRequest request) {
        List<PeisGroupRoster> rosters = requireUnpaidRosters(request);
        PreSettleBatchResponse response = new PreSettleBatchResponse();
        response.setEstimatedCount(rosters.size());
        response.setEstimatedAmount(BigDecimal.ZERO);
        return response;
    }

    /**
     * 确认批量结算（GCHG-05）。
     */
    public ConfirmGroupBatchSettlementResponse confirmBatchSettlement(ConfirmGroupBatchSettlementRequest request) {
        if (request == null || request.getRosterIds() == null || request.getRosterIds().isEmpty()) {
            throw new ValidationException("批量收费失败：请勾选名册");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "请先登录后再收费");
        ConfirmGroupBatchSettlementResponse response = new ConfirmGroupBatchSettlementResponse();
        int successCount = 0;
        List<GroupChargeFailureRowDto> failures = new ArrayList<>();
        for (Long rosterId : request.getRosterIds()) {
            if (rosterId == null) {
                continue;
            }
            try {
                groupChargeSettlementExecutor.settleOneRoster(rosterId, request.getOrgId(), operatorId);
                successCount++;
            } catch (RuntimeException ex) {
                GroupChargeFailureRowDto failure = new GroupChargeFailureRowDto();
                failure.setRosterId(rosterId);
                failure.setMessage(ex.getMessage() != null ? ex.getMessage() : "结算失败");
                failures.add(failure);
            }
        }
        response.setSuccessCount(successCount);
        response.setFailures(failures);
        return response;
    }

    /**
     * 刷新名册收费态（GCHG-06）。
     */
    public RosterChargeStatusResponse refreshRosterChargeStatus(RefreshRosterChargeStatusRequest request) {
        if (request == null || request.getRosterId() == null) {
            throw new ValidationException("刷新收费态失败：名册标识不能为空");
        }
        PeisGroupRoster roster = peisGroupCheckQueryService.requireRoster(request.getRosterId());
        RosterChargeStatusResponse response = new RosterChargeStatusResponse();
        response.setRosterId(roster.getId());
        response.setPayStatusCode(roster.getPayStatusCode());
        response.setEncounterId(roster.getEncounterId());
        response.setPaid(PeisGroupRosterConstants.PAY_PAID.equals(roster.getPayStatusCode()));
        return response;
    }

    private PagedResult<GroupRosterRowDto> pageRosterByPayStatus(
            PageGroupChargeRosterRequest request, String payStatusCode) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("查询名册失败：批次不能为空");
        }
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        Page<PeisGroupRoster> page = peisGroupCheckQueryService.pageRoster(
            request.getBatchId(),
            payStatusCode,
            request.getCollegeId(),
            request.getClassName(),
            pageable);
        List<GroupRosterRowDto> rows = new ArrayList<>();
        for (PeisGroupRoster entity : page.getContent()) {
            rows.add(toRosterRowDto(entity));
        }
        return PagedResult.of(rows, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    private List<PeisGroupRoster> requireUnpaidRosters(PreSettleBatchRequest request) {
        if (request == null || request.getRosterIds() == null || request.getRosterIds().isEmpty()) {
            throw new ValidationException("预结算失败：请勾选名册");
        }
        List<PeisGroupRoster> rosters = peisGroupCheckQueryService.listByIds(request.getRosterIds());
        List<PeisGroupRoster> unpaid = new ArrayList<>();
        for (PeisGroupRoster roster : rosters) {
            if (roster == null) {
                continue;
            }
            if (PeisGroupRosterConstants.PAY_PAID.equals(roster.getPayStatusCode())) {
                throw new ValidationException("预结算失败：存在已收费人员");
            }
            if (roster.getPatientId() == null) {
                throw new ValidationException("预结算失败：请先绑定患者档案");
            }
            unpaid.add(roster);
        }
        if (unpaid.isEmpty()) {
            throw new ValidationException("预结算失败：有效名册不能为空");
        }
        return unpaid;
    }

    private static GroupRosterRowDto toRosterRowDto(PeisGroupRoster entity) {
        GroupRosterRowDto dto = new GroupRosterRowDto();
        dto.setRosterId(entity.getId());
        dto.setBatchId(entity.getBatchId());
        dto.setPatientId(entity.getPatientId());
        dto.setFullName(entity.getFullName());
        dto.setIdCardNo(entity.getIdCardNo());
        dto.setStudentNo(entity.getStudentNo());
        dto.setEmployeeNo(entity.getEmployeeNo());
        dto.setCollegeId(entity.getCollegeId());
        dto.setClassName(entity.getClassName());
        dto.setMobilePhone(entity.getMobilePhone());
        dto.setEncounterId(entity.getEncounterId());
        dto.setPayStatusCode(entity.getPayStatusCode());
        dto.setArrivalStatusCode(entity.getArrivalStatusCode());
        return dto;
    }
}
