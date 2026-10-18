/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.repository;

import com.zxplat.plugin.chs.domain.catalog.entity.YbInstitutionCatalogMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 1317 医药机构目录匹配镜像 JPA 仓储。
 */
public interface YbInstitutionCatalogMatchRepository
    extends JpaRepository<YbInstitutionCatalogMatch, Long>,
    JpaSpecificationExecutor<YbInstitutionCatalogMatch> {

    /**
     * 按唯一业务键查找未软删行（upsert 定位）。
     *
     * @param contractId     契约主键
     * @param tenantId       租户
     * @param medinsListCodg 院内目录编号
     * @param medListCodg    医保目录编码
     * @param listType       目录类别
     * @return 已有行
     */
    Optional<YbInstitutionCatalogMatch>
        findByContractIdAndTenantIdAndMedinsListCodgAndMedListCodgAndListTypeAndIsDeletedFalse(
            Long contractId,
            Long tenantId,
            String medinsListCodg,
            String medListCodg,
            String listType);
}
