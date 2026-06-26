/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.repository;

import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 首页诊断行仓储。
 */
public interface HomepageDiagnosisRepository extends JpaRepository<HomepageDiagnosis, Long> {

    /**
     * 按病案主档查全部有效诊断行。
     *
     * @param caseRecordId 主档主键
     * @return 诊断行列表
     */
    List<HomepageDiagnosis> findByCaseRecordIdAndIsDeletedFalseOrderByDiagTypeCodeAscSortNoAsc(
            Long caseRecordId);

    /**
     * 软删病案下全部诊断行（保存前整表替换用）。
     *
     * @param caseRecordId 主档主键
     * @return 命中行
     */
    List<HomepageDiagnosis> findByCaseRecordIdAndIsDeletedFalse(Long caseRecordId);
}
