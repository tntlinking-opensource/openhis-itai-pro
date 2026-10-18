/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.encounterext.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.plugin.peis.domain.encounterext.command.CreatePeEncounterExtCommand;
import com.zxplat.plugin.peis.domain.encounterext.command.UpdatePePackageSnapshotCommand;
import com.zxplat.plugin.peis.domain.encounterext.entity.PeisEncounterExt;
import com.zxplat.plugin.peis.domain.encounterext.repository.PeisEncounterExtRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.events.custom.peisencounterext.PeisEncounterExtCreatedEvent;
import com.zxplat.domain.share.events.custom.peisencounterext.PeisEncounterPackageAdjustedEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * PEIS 就诊扩展写侧领域服务（DM-PEIS-007）。
 */
@Service
@RequiredArgsConstructor
public class PeisEncounterExtDomainService {

    private final PeisEncounterExtRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 登记时创建就诊扩展（已存在则失败）。
     *
     * @param cmd 创建命令
     * @return 持久化后实体
     */
    public PeisEncounterExt createForRegistration(CreatePeEncounterExtCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getEncounterId() == null || cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_INVALID_ARGUMENT, "就诊与租户标识不能为空");
        }
        if (cmd.getGroupFlag() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_INVALID_ARGUMENT, "团检标志不能为空");
        }
        if (repository.existsByEncounterIdAndIsDeletedFalse(cmd.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_ALREADY_EXISTS, "就诊扩展已存在");
        }

        // 校验团检 → insert → 事件
        LocalDateTime now = LocalDateTime.now();
        PeisEncounterExt entity = new PeisEncounterExt();
        entity.setEncounterId(cmd.getEncounterId());
        entity.markNew();
        entity.setTenantId(cmd.getTenantId());
        entity.setOrgId(cmd.getOrgId());
        entity.setPackageActivityId(cmd.getPackageActivityId());
        entity.setPackageSnapshotJson(trimToNull(cmd.getPackageSnapshotJson()));
        entity.setGroupFlag(cmd.getGroupFlag());
        entity.setCompanyId(cmd.getCompanyId());
        entity.setContractId(cmd.getContractId());
        entity.setGroupBatchId(cmd.getGroupBatchId());
        entity.setBookingAppointmentId(cmd.getBookingAppointmentId());
        entity.setBusNo(trimToNull(cmd.getBusNo()));
        entity.setReferrerName(trimToNull(cmd.getReferrerName()));
        entity.setBreakfastFlag(cmd.getBreakfastFlag());
        entity.setIsDeleted(Boolean.FALSE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.assertGroupCompany();

        PeisEncounterExt saved = repository.save(entity);
        domainEventPublisher.publish(new PeisEncounterExtCreatedEvent(
            saved.getEncounterId(), saved.getGroupFlag(), saved.getPackageActivityId()));
        return saved;
    }

    /**
     * 加项减项后刷新套餐快照。
     *
     * @param cmd 更新命令
     * @return 更新后实体
     */
    public PeisEncounterExt updatePackageSnapshot(UpdatePePackageSnapshotCommand cmd) {
        // 前置校验
        if (cmd == null || cmd.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (StringUtils.isEmpty(trimToNull(cmd.getPackageSnapshotJson()))) {
            throw new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_INVALID_ARGUMENT, "套餐快照不能为空");
        }

        PeisEncounterExt entity = repository.findByEncounterIdAndIsDeletedFalse(cmd.getEncounterId())
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PEIS_ENC_EXT_NOT_FOUND, "就诊扩展不存在"));
        entity.updatePackageSnapshot(cmd.getPackageActivityId(), trimToNull(cmd.getPackageSnapshotJson()));
        entity.setUpdatedAt(LocalDateTime.now());

        PeisEncounterExt saved = repository.save(entity);
        domainEventPublisher.publish(new PeisEncounterPackageAdjustedEvent(saved.getEncounterId()));
        return saved;
    }
}
