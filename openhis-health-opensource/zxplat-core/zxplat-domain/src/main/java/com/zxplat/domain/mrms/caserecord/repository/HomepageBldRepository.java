/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.repository;

import com.zxplat.domain.mrms.caserecord.entity.HomepageBld;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 首页输血行仓储。
 */
public interface HomepageBldRepository extends JpaRepository<HomepageBld, Long> {

    /**
     * 按病案主档查全部有效输血行（按序号）。
     *
     * @param caseRecordId 主档主键
     * @return 输血行列表
     */
    List<HomepageBld> findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(Long caseRecordId);

    /**
     * 查病案下全部有效输血行（保存前整表替换用）。
     *
     * @param caseRecordId 主档主键
     * @return 输血行列表
     */
    List<HomepageBld> findByCaseRecordIdAndIsDeletedFalse(Long caseRecordId);
}
