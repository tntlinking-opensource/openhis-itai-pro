/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.repository;

import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 首页诊断行仓储。
 */
public interface HomepageDiagnosisRepository extends JpaRepository<HomepageDiagnosis, Long> {

    /**
     * 按病案主档查全部有效诊断行，按 {@code sort_no} 升序。
     * <p>顺序与病案首页「上移/下移」维护一致；保存后回显不得再按诊断类型重排打断人工序。
     *
     * @param caseRecordId 主档主键
     * @return 诊断行列表（未软删）
     */
    List<HomepageDiagnosis> findByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(Long caseRecordId);

    /**
     * 软删病案下全部诊断行（保存前整表替换用）。
     *
     * @param caseRecordId 主档主键
     * @return 命中行
     */
    List<HomepageDiagnosis> findByCaseRecordIdAndIsDeletedFalse(Long caseRecordId);
}
