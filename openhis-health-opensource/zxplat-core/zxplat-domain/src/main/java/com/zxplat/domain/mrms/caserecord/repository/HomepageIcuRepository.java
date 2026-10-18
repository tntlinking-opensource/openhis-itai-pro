/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.repository;

import com.zxplat.domain.mrms.caserecord.entity.HomepageIcu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 首页重症监护行仓储。
 */
public interface HomepageIcuRepository extends JpaRepository<HomepageIcu, Long> {

    /**
     * 按病案主档查全部有效 ICU 行（按序号）。
     *
     * @param caseRecordId 主档主键
     * @return ICU 行列表
     */
    List<HomepageIcu> findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(Long caseRecordId);

    /**
     * 查病案下全部有效 ICU 行（保存前整表替换用）。
     *
     * @param caseRecordId 主档主键
     * @return ICU 行列表
     */
    List<HomepageIcu> findByCaseRecordIdAndIsDeletedFalse(Long caseRecordId);
}
