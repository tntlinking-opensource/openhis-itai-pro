/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.device.service;

import com.openhis.health.domain.pacs.device.command.SavePacsDeviceCommand;
import com.openhis.health.domain.pacs.device.entity.PacsDevice;
import com.openhis.health.domain.pacs.device.repository.PacsDeviceRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 检查设备台账写侧（DM-PACS-005）。
 *
 * <p>列表读侧见 {@link PacsDeviceQueryService}。</p>
 */
@Service
@RequiredArgsConstructor
public class PacsDeviceDomainService {

    private final PacsDeviceRepository deviceRepository;

    /**
     * 新增或更新。
     *
     * @param cmd 命令
     * @return 实体
     */
    public PacsDevice save(SavePacsDeviceCommand cmd) {
        // 前置校验：编码/名称/模态/租户必填
        if (cmd == null
            || StringUtils.isEmpty(cmd.getDeviceCode())
            || StringUtils.isEmpty(cmd.getName())
            || StringUtils.isEmpty(cmd.getModalityCode())) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_DEVICE_INVALID_ARGUMENT, "设备编码、名称、模态不能为空");
        }
        if (cmd.getTenantId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_DEVICE_INVALID_ARGUMENT, "租户不能为空");
        }
        String deviceCode = cmd.getDeviceCode().trim();
        // 租户内 deviceCode 唯一，避免同码双设备
        this.assertDeviceCodeUnique(cmd.getTenantId(), deviceCode, cmd.getId());

        PacsDevice entity;
        // 加载/组装聚合：有 id 则更新，否则新建
        if (cmd.getId() != null) {
            entity = deviceRepository.findById(cmd.getId())
                .orElseThrow(() -> new BusinessException(
                    ErrorCodeConstants.PACS_DEVICE_NOT_FOUND, "设备不存在"));
        } else {
            entity = new PacsDevice();
            entity.setTenantId(cmd.getTenantId());
            entity.setCreatedBy(cmd.getOperatorId());
            if (entity.getIsDeleted() == null) {
                entity.setIsDeleted(Boolean.FALSE);
            }
        }
        entity.setDeviceCode(deviceCode);
        entity.setName(cmd.getName().trim());
        entity.setModalityCode(cmd.getModalityCode().trim());
        if (StringUtils.isNotEmpty(cmd.getAeTitle())) {
            entity.setAeTitle(cmd.getAeTitle().trim());
        } else {
            entity.setAeTitle(null);
        }
        if (StringUtils.isNotEmpty(cmd.getStationName())) {
            entity.setStationName(cmd.getStationName().trim());
        } else {
            entity.setStationName(null);
        }
        Boolean enabledFlag = Boolean.TRUE;
        if (cmd.getEnabledFlag() != null) {
            enabledFlag = cmd.getEnabledFlag();
        }
        entity.setEnabledFlag(enabledFlag);
        entity.setSortOrder(cmd.getSortOrder());
        entity.setRemark(cmd.getRemark());
        entity.setUpdatedBy(cmd.getOperatorId());
        entity.setUpdatedAt(LocalDateTime.now());
        // 持久化
        return deviceRepository.save(entity);
    }

    /**
     * 停用。
     *
     * @param id 主键
     * @param operatorId 操作人
     * @return 实体
     */
    public PacsDevice disable(Long id, Long operatorId) {
        PacsDevice entity = this.getById(id);
        entity.setEnabledFlag(Boolean.FALSE);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        return deviceRepository.save(entity);
    }

    /**
     * 软删。
     *
     * @param id 主键
     * @param operatorId 操作人
     */
    public void delete(Long id, Long operatorId) {
        PacsDevice entity = this.getById(id);
        entity.setIsDeleted(Boolean.TRUE);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDeletedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(LocalDateTime.now());
        deviceRepository.save(entity);
    }

    /**
     * 按主键加载（未软删由 Repository 约定过滤）。
     *
     * @param id 主键
     * @return 实体
     */
    public PacsDevice getById(Long id) {
        if (id == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_DEVICE_INVALID_ARGUMENT, "设备主键不能为空");
        }
        return deviceRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PACS_DEVICE_NOT_FOUND, "设备不存在"));
    }

    /**
     * 租户内未软删 deviceCode 唯一（BR-DEV-02）。
     *
     * @param tenantId 租户
     * @param deviceCode 设备编码
     * @param excludeId 更新时排除自身主键；新建传 null
     */
    private void assertDeviceCodeUnique(Long tenantId, String deviceCode, Long excludeId) {
        Optional<PacsDevice> existing =
            deviceRepository.findByTenantIdAndDeviceCode(tenantId, deviceCode);
        if (existing.isEmpty()) {
            return;
        }
        PacsDevice other = existing.get();
        // 更新自身：同码同 id 放行
        if (excludeId != null && excludeId.equals(other.getId())) {
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.PACS_DEVICE_CODE_DUPLICATE, "同租户设备编码已存在");
    }
}
