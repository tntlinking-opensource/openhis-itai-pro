/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.repository;

import com.zxplat.domain.clinical.allergyintolerance.entity.AllergyIntolerance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 过敏与不耐受仓储。
 */
public interface AllergyIntoleranceRepository extends JpaRepository<AllergyIntolerance, Long> {
    /**
     * 按主键查询。
     * @param id 条件
     * @return 查询结果
     */
    Optional<AllergyIntolerance> findById(Long id);

    /**
     * 按条件查询。
     * @param encounterId 条件
     * @return 查询结果
     */
    List<AllergyIntolerance> findByEncounterId(Long encounterId);

    /**
     * 按条件查询。
     * @param patientId 条件
     * @param encounterId 条件
     * @return 查询结果
     */
    List<AllergyIntolerance> findByPatientIdAndEncounterId(Long patientId, Long encounterId);

    /**
     * 按患者加载过敏记录（软删过滤器开启时不含已删行）。
     *
     * @param patientId 患者主键
     * @return 该患者全部可见记录，规模为单患者过敏条数
     */
    List<AllergyIntolerance> findByPatientId(Long patientId);
}
