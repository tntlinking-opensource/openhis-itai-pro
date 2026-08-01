/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 科室对照仓储。
 */
public interface CrbDeptCodeMapRepository extends JpaRepository<CrbDeptCodeMap, Long>,
        JpaSpecificationExecutor<CrbDeptCodeMap> {

    /**
     * 按租户与院内科室业务码查找未删除对照。
     *
     * @param tenantId 租户 id
     * @param locationBusNo 院内科室业务码
     * @return 对照行
     */
    Optional<CrbDeptCodeMap> findByTenantIdAndLocationBusNoAndIsDeletedFalse(
            Long tenantId, String locationBusNo);

    /**
     * 按租户与院内科室业务码查找（含已软删，用于 upsert 复活）。
     *
     * @param tenantId 租户 id
     * @param locationBusNo 院内科室业务码
     * @return 对照行
     */
    Optional<CrbDeptCodeMap> findByTenantIdAndLocationBusNo(Long tenantId, String locationBusNo);

    /**
     * 按主键与租户加载未删除对照。
     *
     * @param id 主键
     * @param tenantId 租户 id
     * @return 对照行
     */
    Optional<CrbDeptCodeMap> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    /**
     * 按主键与租户加载对照（含已软删）。
     *
     * @param id 主键
     * @param tenantId 租户 id
     * @return 对照行
     */
    Optional<CrbDeptCodeMap> findByIdAndTenantId(Long id, Long tenantId);
}
