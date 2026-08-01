/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 检验项目对照仓储。
 */
public interface CrbLabItemMapRepository extends JpaRepository<CrbLabItemMap, Long>,
        JpaSpecificationExecutor<CrbLabItemMap> {

    /**
     * 按租户与院内项目业务码查找未删除对照。
     *
     * @param tenantId 租户 id
     * @param itemBusNo 院内项目业务码
     * @return 对照行
     */
    Optional<CrbLabItemMap> findByTenantIdAndItemBusNoAndIsDeletedFalse(Long tenantId, String itemBusNo);

    /**
     * 按租户与院内项目业务码查找（含已软删，用于 upsert 复活）。
     *
     * @param tenantId 租户 id
     * @param itemBusNo 院内项目业务码
     * @return 对照行
     */
    Optional<CrbLabItemMap> findByTenantIdAndItemBusNo(Long tenantId, String itemBusNo);

    /**
     * 按主键与租户加载未删除对照。
     *
     * @param id 主键
     * @param tenantId 租户 id
     * @return 对照行
     */
    Optional<CrbLabItemMap> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    /**
     * 按主键与租户加载对照（含已软删）。
     *
     * @param id 主键
     * @param tenantId 租户 id
     * @return 对照行
     */
    Optional<CrbLabItemMap> findByIdAndTenantId(Long id, Long tenantId);
}
