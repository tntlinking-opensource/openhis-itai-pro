/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.repository;

import com.zxplat.domain.diagnosis.vitalsign.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 生命体征单据主表仓储（DM-DIA-002 v2.0 §4.5）。
 */
public interface VitalSignRepository extends JpaRepository<VitalSign, Long>, JpaSpecificationExecutor<VitalSign> {

    /**
     * 按就诊查询有效主表，记录时点降序。
     *
     * @param encounterId 就诊标识
     * @return 主表列表
     */
    List<VitalSign> findAllByEncounterIdAndIsDeletedFalseOrderByRecordedAtDesc(Long encounterId);

    /**
     * 按就诊与单据类型查询有效主表，记录时点升序。
     *
     * @param encounterId 就诊标识
     * @param typeEnum    单据类型 code
     * @return 主表列表
     */
    List<VitalSign> findAllByEncounterIdAndTypeEnumAndIsDeletedFalseOrderByRecordedAtAsc(
        Long encounterId, String typeEnum);

    /**
     * 按就诊与记录时点区间查询有效主表，记录时点升序。
     *
     * @param encounterId 就诊标识
     * @param from        起始（含）
     * @param to          结束（含）
     * @return 主表列表
     */
    List<VitalSign> findAllByEncounterIdAndRecordedAtBetweenAndIsDeletedFalseOrderByRecordedAtAsc(
        Long encounterId, LocalDateTime from, LocalDateTime to);

    /**
     * 按患者与记录时点区间查询有效主表，记录时点降序。
     *
     * @param patientId 患者标识
     * @param from      起始（含）
     * @param to        结束（含）
     * @return 主表列表
     */
    List<VitalSign> findAllByPatientIdAndRecordedAtBetweenAndIsDeletedFalseOrderByRecordedAtDesc(
        Long patientId, LocalDateTime from, LocalDateTime to);

    /**
     * 按主键查询有效主表。
     *
     * @param id 主表 id
     * @return 主表
     */
    Optional<VitalSign> findByIdAndIsDeletedFalse(Long id);

    /**
     * 按主键集合查询有效主表。
     *
     * @param ids 主键集合
     * @return 主表列表
     */
    List<VitalSign> findAllByIdInAndIsDeletedFalse(Collection<Long> ids);

    /**
     * 判断租户下单号是否已存在。
     *
     * @param tenantId 租户 id
     * @param busNo    业务单号
     * @return 是否存在
     */
    boolean existsByTenantIdAndBusNoAndIsDeletedFalse(Long tenantId, String busNo);

    /**
     * 判断租户下单号是否被其他主表占用。
     *
     * @param tenantId 租户 id
     * @param busNo    业务单号
     * @param id       排除的主表 id
     * @return 是否存在冲突
     */
    boolean existsByTenantIdAndBusNoAndIsDeletedFalseAndIdNot(Long tenantId, String busNo, Long id);

    /**
     * 判断就诊在某记录时点是否存在有效主表。
     *
     * @param encounterId 就诊标识
     * @param recordedAt  记录时点
     * @return 是否存在
     */
    boolean existsByEncounterIdAndRecordedAtAndIsDeletedFalse(Long encounterId, LocalDateTime recordedAt);

    /**
     * 按就诊与记录时点查询有效主表。
     *
     * @param encounterId 就诊标识
     * @param recordedAt  记录时点
     * @return 主表列表
     */
    List<VitalSign> findAllByEncounterIdAndRecordedAtAndIsDeletedFalse(Long encounterId, LocalDateTime recordedAt);
}
