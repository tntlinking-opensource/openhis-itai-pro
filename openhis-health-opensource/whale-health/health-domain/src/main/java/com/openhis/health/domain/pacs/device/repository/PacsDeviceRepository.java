/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.device.repository;

import com.openhis.health.domain.pacs.device.entity.PacsDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 检查设备台账仓储。
 */
public interface PacsDeviceRepository
    extends JpaRepository<PacsDevice, Long>, JpaSpecificationExecutor<PacsDevice> {

    /**
     * 按租户与设备编码查唯一台账（底座已滤软删）。
     *
     * @param tenantId 租户
     * @param deviceCode 设备编码
     * @return 设备
     */
    Optional<PacsDevice> findByTenantIdAndDeviceCode(Long tenantId, String deviceCode);
}
