/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.encounter.repository;

import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 就诊诊断关联仓储。
 */
public interface EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, Long> {

    /**
     * 按主键集合批量加载就诊诊断关联行。
     *
     * @param ids 关联表主键集合
     * @return 实体列表
     */
    List<EncounterDiagnosis> findAllByIdIn(Collection<Long> ids);

    /**
     * 查询就诊下全部未删除诊断关联。
     *
     * @param encounterId 就诊标识
     * @return 诊断关联列表
     */
    List<EncounterDiagnosis> findAllByEncounterId(Long encounterId);

    /**
     * 查询就诊下主诊断记录。
     *
     * @param encounterId 就诊标识
     * @return 主诊断记录
     */
    Optional<EncounterDiagnosis> findFirstByEncounterIdAndPrimaryFlagTrue(Long encounterId);

    /**
     * 按就诊和诊断标识查询记录。
     *
     * @param encounterId 就诊标识
     * @param conditionId 诊断标识
     * @return 诊断关联记录
     */
    Optional<EncounterDiagnosis> findByEncounterIdAndConditionId(Long encounterId, Long conditionId);

    /**
     * 按病情事实主键批量加载就诊诊断关联（患者历史病证组反查）。
     *
     * @param conditionIds 病情事实主键集合
     * @return 关联行列表；入参为空时由调用方保证不入库
     */
    List<EncounterDiagnosis> findAllByConditionIdIn(Collection<Long> conditionIds);
}

