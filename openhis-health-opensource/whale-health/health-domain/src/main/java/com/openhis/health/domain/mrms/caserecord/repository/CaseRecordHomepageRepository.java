/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.repository;

import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 病案首页仓储。
 */
public interface CaseRecordHomepageRepository extends JpaRepository<CaseRecordHomepage, Long> {

    /**
     * 按病案主档查有效首页。
     *
     * @param caseRecordId 主档主键
     * @return 首页
     */
    Optional<CaseRecordHomepage> findByCaseRecordIdAndIsDeletedFalse(Long caseRecordId);
}
