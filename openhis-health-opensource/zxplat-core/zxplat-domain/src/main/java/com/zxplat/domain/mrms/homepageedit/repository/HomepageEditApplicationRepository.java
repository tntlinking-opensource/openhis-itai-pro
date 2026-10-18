/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.homepageedit.repository;

import com.zxplat.domain.mrms.homepageedit.entity.HomepageEditApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 首页编辑申请仓储（仅本聚合；禁 {@code @Query}）。
 */
public interface HomepageEditApplicationRepository
        extends JpaRepository<HomepageEditApplication, Long>, JpaSpecificationExecutor<HomepageEditApplication> {

    /**
     * 按主键加载未删申请。
     *
     * @param id 主键
     * @return 申请
     */
    Optional<HomepageEditApplication> findByIdAndIsDeletedFalse(Long id);

    /**
     * 同病案在途申请（DRAFTING / PENDING_REVIEW）。
     *
     * @param tenantId 租户
     * @param caseRecordId 病案
     * @param statusEnums 状态 code 列表
     * @return 在途列表
     */
    List<HomepageEditApplication> findByTenantIdAndCaseRecordIdAndStatusEnumInAndIsDeletedFalse(
            Long tenantId, Long caseRecordId, List<String> statusEnums);

    /**
     * 按病案列历史（未删）。
     *
     * @param caseRecordId 病案
     * @return 历史列表
     */
    List<HomepageEditApplication> findByCaseRecordIdAndIsDeletedFalseOrderByAppliedAtDesc(Long caseRecordId);
}
