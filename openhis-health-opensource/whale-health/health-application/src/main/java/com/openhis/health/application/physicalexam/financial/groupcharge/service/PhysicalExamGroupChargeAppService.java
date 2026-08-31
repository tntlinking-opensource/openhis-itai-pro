/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.service;

import com.openhis.health.application.peis.group.dto.GroupRosterRowDto;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.ConfirmGroupBatchSettlementRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.PageGroupChargeRosterRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.PreSettleBatchRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.request.RefreshRosterChargeStatusRequest;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.ConfirmGroupBatchSettlementResponse;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.GroupChargePageInitResponse;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.PreSettleBatchResponse;
import com.openhis.health.application.physicalexam.financial.groupcharge.response.RosterChargeStatusResponse;
import com.openhis.health.domain.peis.group.entity.PeisGroupRoster;
import com.openhis.health.domain.peis.group.service.PeisGroupCheckQueryService;
import com.openhis.health.domain.share.constants.PeisGroupRosterConstants;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检批量收费应用服务（AS-PE-FIN-002）。
 *
 * <p>写侧已冻结；主路径为筹备免收费建单 + 单位合同后结。读侧名册查询仍可用。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class PhysicalExamGroupChargeAppService {

    private static final String FROZEN_MESSAGE =
        "团检批量收费已停用，请使用团检筹备免收费建单，单位款项在合同上后结确认";

    private final PeisGroupCheckQueryService peisGroupCheckQueryService;

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
        throw new BusinessException("PEIS_GROUP_CHARGE_FROZEN", FROZEN_MESSAGE);
    }

    /**
     * 确认批量结算（GCHG-05）。
     */
    public ConfirmGroupBatchSettlementResponse confirmBatchSettlement(ConfirmGroupBatchSettlementRequest request) {
        throw new BusinessException("PEIS_GROUP_CHARGE_FROZEN", FROZEN_MESSAGE);
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
        response.setPaid(PeisGroupRosterConstants.isEnrolled(roster.getPayStatusCode()));
        return response;
    }

    private PagedResult<GroupRosterRowDto> pageRosterByPayStatus(
            PageGroupChargeRosterRequest request, String payStatusFilter) {
        if (request == null || request.getBatchId() == null) {
            throw new ValidationException("查询名册失败：批次不能为空");
        }
        String enrollmentFilter = null;
        String payStatusCode = payStatusFilter;
        if (PeisGroupRosterConstants.PAY_UNPAID.equals(payStatusFilter)) {
            payStatusCode = null;
            enrollmentFilter = "UNENROLLED";
        }
        PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
        Page<PeisGroupRoster> page = peisGroupCheckQueryService.pageRoster(
            request.getBatchId(),
            payStatusCode,
            enrollmentFilter,
            null,
            request.getCollegeId(),
            request.getClassName(),
            pageable);
        List<GroupRosterRowDto> rows = new ArrayList<>();
        for (PeisGroupRoster entity : page.getContent()) {
            rows.add(toRosterRowDto(entity));
        }
        return PagedResult.of(rows, page.getTotalElements(), page.getNumber() + 1, page.getSize());
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
