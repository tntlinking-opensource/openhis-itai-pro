/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 药品对照仓储。
 */
public interface CrbDrugCodeMapRepository extends JpaRepository<CrbDrugCodeMap, Long>,
        JpaSpecificationExecutor<CrbDrugCodeMap> {

    /**
     * 按租户与院内药品业务码查找未删除对照。
     *
     * @param tenantId 租户 id
     * @param medBusNo 院内药品业务码
     * @return 对照行
     */
    Optional<CrbDrugCodeMap> findByTenantIdAndMedBusNoAndIsDeletedFalse(Long tenantId, String medBusNo);

    /**
     * 按租户与院内药品业务码查找（含已软删，用于 upsert 复活）。
     *
     * @param tenantId 租户 id
     * @param medBusNo 院内药品业务码
     * @return 对照行
     */
    Optional<CrbDrugCodeMap> findByTenantIdAndMedBusNo(Long tenantId, String medBusNo);

    /**
     * 按主键与租户加载未删除对照。
     *
     * @param id 主键
     * @param tenantId 租户 id
     * @return 对照行
     */
    Optional<CrbDrugCodeMap> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    /**
     * 按主键与租户加载对照（含已软删）。
     *
     * @param id 主键
     * @param tenantId 租户 id
     * @return 对照行
     */
    Optional<CrbDrugCodeMap> findByIdAndTenantId(Long id, Long tenantId);
}
