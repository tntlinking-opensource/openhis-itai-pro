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

import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
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
}

