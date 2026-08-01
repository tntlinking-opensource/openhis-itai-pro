/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gfratiorequest.service;

import com.openhis.campus.domain.gfchargeratioitem.service.GfChargeRatioItemQueryService;
import com.openhis.campus.domain.gfchargeratioitem.service.GfChargeRatioItemDomainService;
import com.openhis.campus.domain.gfratiorequest.entity.GfRatioRequest;
import com.openhis.campus.domain.gfratiorequest.repository.GfRatioRequestRepository;
import com.openhis.campus.domain.shared.CampusGfConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 公费比例调整申请写侧。
 */
@Service
@RequiredArgsConstructor
public class GfRatioRequestDomainService {

    private final GfRatioRequestRepository repository;
    private final GfChargeRatioItemQueryService gfChargeRatioItemQueryService;
    private final GfChargeRatioItemDomainService gfChargeRatioItemDomainService;

    public GfRatioRequest createRequest(GfRatioRequest incoming, Long operatorId, String applicantName) {
        CampusGfConstants.validateChargeMode(incoming.getChargeModeEnum());
        CampusGfConstants.validateRatio(incoming.getNewRatio());
        if (incoming.getItemCode() == null || incoming.getItemCode().isBlank()) {
            throw new BusinessException("ITEM_CODE_REQUIRED", "项目编码不能为空");
        }
        Long tenantId = resolveTenantId(incoming.getTenantId());
        BigDecimal oldRatio = incoming.getOldRatio();
        if (oldRatio == null) {
            oldRatio = gfChargeRatioItemQueryService.getCurrent(
                    tenantId, incoming.getItemCode(), incoming.getChargeModeEnum(), LocalDateTime.now())
                .map(item -> item.getRatio())
                .orElse(null);
        }
        GfRatioRequest entity = new GfRatioRequest();
        entity.setTenantId(tenantId);
        entity.setRequestNo(generateRequestNo());
        entity.setChargeModeEnum(incoming.getChargeModeEnum());
        entity.setItemCode(incoming.getItemCode());
        entity.setOldRatio(oldRatio);
        entity.setNewRatio(incoming.getNewRatio());
        entity.setStatusEnum(CampusGfConstants.REQUEST_STATUS_PENDING);
        entity.setApplicantId(operatorId);
        entity.setApplicantName(applicantName);
        entity.setRemark(incoming.getRemark());
        touchAudit(entity, operatorId, true);
        return repository.save(entity);
    }

    @Transactional
    public GfRatioRequest approve(Long tenantId, Long requestId, Long operatorId, String comment) {
        GfRatioRequest request = requirePending(resolveTenantId(tenantId), requestId);
        gfChargeRatioItemDomainService.applyApproved(
            request.getTenantId(),
            request.getItemCode(),
            request.getChargeModeEnum(),
            request.getNewRatio(),
            null,
            null,
            operatorId);
        request.setStatusEnum(CampusGfConstants.REQUEST_STATUS_APPROVED);
        request.setApproveTime(LocalDateTime.now());
        if (comment != null && !comment.isBlank()) {
            request.setRemark(mergeRemark(request.getRemark(), comment));
        }
        touchAudit(request, operatorId, false);
        return repository.save(request);
    }

    public GfRatioRequest reject(Long tenantId, Long requestId, Long operatorId, String comment) {
        GfRatioRequest request = requirePending(resolveTenantId(tenantId), requestId);
        request.setStatusEnum(CampusGfConstants.REQUEST_STATUS_REJECTED);
        request.setApproveTime(LocalDateTime.now());
        if (comment != null && !comment.isBlank()) {
            request.setRemark(mergeRemark(request.getRemark(), comment));
        }
        touchAudit(request, operatorId, false);
        return repository.save(request);
    }

    private GfRatioRequest requirePending(Long tenantId, Long requestId) {
        if (tenantId == null || requestId == null) {
            throw new BusinessException("GF_REQUEST_NOT_FOUND", "申请单不存在");
        }
        GfRatioRequest request = repository.findByIdAndTenantIdAndIsDeletedFalse(requestId, tenantId)
            .orElseThrow(() -> new BusinessException("GF_REQUEST_NOT_FOUND", "申请单不存在"));
        if (!CampusGfConstants.REQUEST_STATUS_PENDING.equals(request.getStatusEnum())) {
            throw new BusinessException("GF_REQUEST_STATUS_INVALID", "申请单状态不可审批");
        }
        return request;
    }

    private String generateRequestNo() {
        return "GFR" + System.currentTimeMillis();
    }

    private String mergeRemark(String existing, String comment) {
        if (existing == null || existing.isBlank()) {
            return comment;
        }
        return existing + " | " + comment;
    }

    private void touchAudit(GfRatioRequest entity, Long operatorId, boolean creating) {
        LocalDateTime now = LocalDateTime.now();
        if (creating) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
