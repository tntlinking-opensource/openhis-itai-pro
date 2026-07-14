/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.repository;

import com.openhis.health.domain.administration.patient.entity.PatientStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 患者学生子实体仓储。
 */
public interface PatientStudentRepository extends JpaRepository<PatientStudent, Long> {

    /**
     * 查询患者有效学籍记录。
     *
     * @param patientId 患者标识
     * @return 学籍记录列表
     */
    List<PatientStudent> findAllByPatientId(Long patientId);

    /**
     * 批量查询患者有效学籍记录。
     *
     * @param patientIds 患者标识集合
     * @return 学籍记录列表
     */
    List<PatientStudent> findAllByPatientIdIn(Collection<Long> patientIds);

    /**
     * 按主键集合查询未删除的学生记录。
     *
     * @param ids 学籍记录标识集合
     * @return 学籍记录列表
     */
    List<PatientStudent> findAllByIdIn(Collection<Long> ids);

    /**
     * 按患者和子实体主键查询。
     *
     * @param id 学籍记录标识
     * @param patientId 患者标识
     * @return 学籍记录
     */
    Optional<PatientStudent> findByIdAndPatientId(Long id, Long patientId);
}
