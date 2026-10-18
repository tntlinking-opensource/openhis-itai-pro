/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.servicerequestext.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.peis.domain.servicerequestext.command.InitPeisSrExtCommand;
import com.zxplat.plugin.peis.domain.servicerequestext.command.UpdatePeisSrDispatchCommand;
import com.zxplat.plugin.peis.domain.servicerequestext.command.UpdatePeisSrExecStatusCommand;
import com.zxplat.plugin.peis.domain.servicerequestext.entity.PeisServiceRequestExt;
import com.zxplat.plugin.peis.domain.servicerequestext.repository.PeisServiceRequestExtRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.PeisDispatchStatusConstants;
import com.zxplat.domain.share.constants.PeisExecStatusConstants;
import com.zxplat.domain.share.events.custom.peisservicerequestext.PeisServiceRequestDispatchChangedEvent;
import com.zxplat.domain.share.events.custom.peisservicerequestext.PeisServiceRequestExecStatusChangedEvent;
import com.zxplat.domain.workflow.servicerequest.entity.ServiceRequest;
import com.zxplat.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * PEIS 服务申请执行扩展写侧领域服务（DM-PEIS-001）。
 */
@Service
@RequiredArgsConstructor
public class PeisServiceRequestExtDomainService {

    private final PeisServiceRequestExtRepository repository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 批量为服务申请初始化执行扩展（已存在则跳过，幂等）。
     *
     * @param cmd 初始化命令
     * @return 本次新建及已存在行的合集
     */
    public List<PeisServiceRequestExt> initForServiceRequests(InitPeisSrExtCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "租户标识不能为空");
        }
        List<Long> srIds = cmd.getServiceRequestIds();
        if (srIds == null || srIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "服务申请列表不能为空");
        }

        // 过滤已有 → batch insert → 返回
        List<PeisServiceRequestExt> existing =
            repository.findByServiceRequestIdInAndIsDeletedFalse(srIds);
        Set<Long> existingIds = new HashSet<>();
        for (PeisServiceRequestExt row : existing) {
            existingIds.add(row.getServiceRequestId());
        }

        List<PeisServiceRequestExt> toInsert = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long srId : srIds) {
            if (srId == null || existingIds.contains(srId)) {
                continue;
            }
            PeisServiceRequestExt ext = new PeisServiceRequestExt();
            ext.setServiceRequestId(srId);
            ext.markNew();
            ext.setTenantId(cmd.getTenantId());
            ext.setOrgId(cmd.getOrgId());
            ext.applyInitDefaults();
            ext.setCreatedAt(now);
            ext.setUpdatedAt(now);
            toInsert.add(ext);
            existingIds.add(srId);
        }

        List<PeisServiceRequestExt> result = new ArrayList<>(existing);
        if (!toInsert.isEmpty()) {
            result.addAll(repository.saveAll(toInsert));
        }
        return result;
    }

    /**
     * 减项时软删执行扩展（与 SR 草稿删除/取消对称）。
     *
     * @param serviceRequestId 服务申请主键
     * @param operatorId       操作人
     */
    public void removeForServiceRequest(Long serviceRequestId, Long operatorId) {
        if (serviceRequestId == null) {
            return;
        }
        PeisServiceRequestExt entity = repository
            .findByServiceRequestIdAndIsDeletedFalse(serviceRequestId)
            .orElse(null);
        if (entity == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(now);
        entity.setDeletedBy(operatorId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        repository.save(entity);
    }

    /**
     * 更新执行状态（确认/弃检/延检）。
     *
     * @param cmd 更新命令
     * @return 更新后实体
     */
    public PeisServiceRequestExt updateExecStatus(UpdatePeisSrExecStatusCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getServiceRequestId() == null || cmd.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "服务申请与就诊标识不能为空");
        }
        String status = trimToNull(cmd.getExecStatusEnum());
        if (StringUtils.isEmpty(status)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "执行状态不能为空");
        }

        // 校验归属 → 迁态 → save → 事件
        ServiceRequest sr = serviceRequestRepository.findById(cmd.getServiceRequestId()).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "服务申请不存在"));
        if (!Objects.equals(sr.getEncounterId(), cmd.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "服务申请与就诊不匹配");
        }

        PeisServiceRequestExt entity = repository
            .findByServiceRequestIdAndIsDeletedFalse(cmd.getServiceRequestId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_NOT_FOUND, "执行扩展不存在"));

        if (PeisExecStatusConstants.CHECKED.equals(status)) {
            entity.markChecked();
        } else if (PeisExecStatusConstants.ABANDONED.equals(status)) {
            entity.markAbandoned(cmd.getReason());
        } else if (PeisExecStatusConstants.DEFERRED.equals(status)) {
            entity.markDeferred(cmd.getReason());
        } else if (PeisExecStatusConstants.PENDING.equals(status)) {
            entity.setExecStatusEnum(PeisExecStatusConstants.PENDING);
        } else {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "执行状态非法");
        }

        entity.setUpdatedAt(LocalDateTime.now());
        PeisServiceRequestExt saved = repository.save(entity);
        domainEventPublisher.publish(new PeisServiceRequestExecStatusChangedEvent(
            saved.getServiceRequestId(), cmd.getEncounterId(), saved.getExecStatusEnum()));
        return saved;
    }

    /**
     * 更新 LIS/PACS 外发标志。
     *
     * @param cmd 更新命令
     * @return 更新后实体
     */
    public PeisServiceRequestExt updateDispatchFlags(UpdatePeisSrDispatchCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getServiceRequestId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "服务申请标识不能为空");
        }
        String module = trimToNull(cmd.getModuleEnum());
        String dispatch = trimToNull(cmd.getDispatchEnum());
        if (StringUtils.isEmpty(module) || StringUtils.isEmpty(dispatch)) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "模块与外发状态不能为空");
        }

        // 校验 module → 更新对应 enum → save → 事件
        PeisServiceRequestExt entity = repository
            .findByServiceRequestIdAndIsDeletedFalse(cmd.getServiceRequestId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_NOT_FOUND, "执行扩展不存在"));

        if (PeisDispatchStatusConstants.MODULE_LIS.equalsIgnoreCase(module)) {
            entity.updateLisDispatch(dispatch);
            module = PeisDispatchStatusConstants.MODULE_LIS;
        } else if (PeisDispatchStatusConstants.MODULE_PACS.equalsIgnoreCase(module)) {
            entity.updatePacsDispatch(dispatch);
            module = PeisDispatchStatusConstants.MODULE_PACS;
        } else {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_SR_EXT_INVALID_ARGUMENT, "模块标识非法，仅支持 LIS/PACS");
        }

        entity.setUpdatedAt(LocalDateTime.now());
        PeisServiceRequestExt saved = repository.save(entity);
        domainEventPublisher.publish(new PeisServiceRequestDispatchChangedEvent(
            saved.getServiceRequestId(), module, dispatch));
        return saved;
    }
}
