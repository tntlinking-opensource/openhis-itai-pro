/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.groupcharge.service;

import com.zxplat.plugin.peis.application.group.dto.GroupRosterRowDto;
import com.zxplat.plugin.peis.application.financial.groupcharge.request.ConfirmGroupBatchSettlementRequest;
import com.zxplat.plugin.peis.application.financial.groupcharge.request.PageGroupChargeRosterRequest;
import com.zxplat.plugin.peis.application.financial.groupcharge.request.PreSettleBatchRequest;
import com.zxplat.plugin.peis.application.financial.groupcharge.request.RefreshRosterChargeStatusRequest;
import com.zxplat.plugin.peis.application.financial.groupcharge.response.ConfirmGroupBatchSettlementResponse;
import com.zxplat.plugin.peis.application.financial.groupcharge.response.GroupChargePageInitResponse;
import com.zxplat.plugin.peis.application.financial.groupcharge.response.PreSettleBatchResponse;
import com.zxplat.plugin.peis.application.financial.groupcharge.response.RosterChargeStatusResponse;
import com.zxplat.plugin.peis.domain.group.entity.PeisGroupRoster;
import com.zxplat.plugin.peis.domain.group.service.PeisGroupCheckQueryService;
import com.zxplat.domain.share.constants.PeisGroupRosterConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
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
    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

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
        // Gate：分页窗口合法（B.10.11）
        pageWindowGate.requirePositivePageWindow(
            request, ErrorCodeConstants.PEIS_REG_INVALID_ARGUMENT, "团检收费名册分页参数非法");
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
        // Asm：页码换算仅走 Builder（B.10.11）
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
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
