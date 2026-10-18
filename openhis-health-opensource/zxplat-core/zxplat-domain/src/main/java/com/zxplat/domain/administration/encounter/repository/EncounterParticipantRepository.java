/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.repository;

import com.zxplat.domain.administration.encounter.entity.EncounterParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 就诊参与者仓储。
 */
public interface EncounterParticipantRepository extends JpaRepository<EncounterParticipant, Long> {

    /**
     * 批量查询就诊下活动参与者（住院上下文批量 enrichment）。
     *
     * @param encounterIds 就诊主键集合
     * @param statusEnum 活动状态
     * @return 参与者列表
     */
    List<EncounterParticipant> findAllByEncounterIdInAndStatusEnum(
        Collection<Long> encounterIds, String statusEnum);

    /**
     * 查询就诊下活动参与者。
     *
     * @param encounterId 就诊标识
     * @param statusEnum 活动状态
     * @return 参与者列表
     */
    List<EncounterParticipant> findAllByEncounterIdAndStatusEnum(Long encounterId, String statusEnum);

    /**
     * 查询就诊下指定角色活动参与者。
     *
     * @param encounterId 就诊标识
     * @param roleCode 角色编码
     * @param statusEnum 活动状态
     * @return 参与者列表
     */
    List<EncounterParticipant> findAllByEncounterIdAndRoleCodeAndStatusEnum(
        Long encounterId, String roleCode, String statusEnum);

    /**
     * 按医务人员主键集合反查指定角色、状态的参与者（门诊病历等按接诊医生收敛就诊）。
     *
     * @param practitionerIds 医务人员主键集合
     * @param roleCode 角色编码（如 ADMITTER）
     * @param statusEnum 活动状态
     * @return 参与者列表；入参空时由调用方短路
     */
    List<EncounterParticipant> findAllByPractitionerIdInAndRoleCodeAndStatusEnum(
        Collection<Long> practitionerIds, String roleCode, String statusEnum);
}
