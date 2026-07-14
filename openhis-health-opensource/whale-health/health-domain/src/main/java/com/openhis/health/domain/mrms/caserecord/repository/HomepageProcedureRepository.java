/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.repository;

import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 首页手术行仓储。
 */
public interface HomepageProcedureRepository extends JpaRepository<HomepageProcedure, Long> {

    /**
     * 按病案主档查全部有效手术行。
     *
     * @param caseRecordId 主档主键
     * @return 手术行列表
     */
    List<HomepageProcedure> findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(Long caseRecordId);

    /**
     * 查病案下全部有效手术行（保存前整表替换用）。
     *
     * @param caseRecordId 主档主键
     * @return 手术行列表
     */
    List<HomepageProcedure> findByCaseRecordIdAndIsDeletedFalse(Long caseRecordId);
}
