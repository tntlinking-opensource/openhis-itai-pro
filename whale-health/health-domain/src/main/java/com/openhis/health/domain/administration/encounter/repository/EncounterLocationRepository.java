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

import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 就诊位置仓储。
 */
public interface EncounterLocationRepository extends JpaRepository<EncounterLocation, Long> {

    /**
     * 按就诊查询位置轨迹。
     *
     * @param encounterId 就诊标识
     * @return 轨迹列表
     */
    List<EncounterLocation> findAllByEncounterIdOrderByStartTimeAsc(Long encounterId);

    /**
     * 查询就诊活动位置。
     *
     * @param encounterId 就诊标识
     * @param statusEnum 活动状态
     * @return 位置列表
     */
    List<EncounterLocation> findAllByEncounterIdAndStatusEnum(Long encounterId, String statusEnum);

    /**
     * 批量查询多个就诊的活动位置。
     */
    List<EncounterLocation> findAllByEncounterIdInAndStatusEnum(Collection<Long> encounterIds, String statusEnum);

    /**
     * 按床位（或其它位置）主键批量查询活动轨迹行。
     *
     * <p>护士站床位列表占床反查（AS-IP-NUR-002 ATD-01）：{@code form_enum} 为床位、{@code status_enum} 为活动。</p>
     *
     * @param locationIds 位置主键集合（通常为 BED 级 {@code adm_location.id}）
     * @param formEnum 位置形态编码（如 {@code BED}）
     * @param statusEnum 轨迹活动状态编码（如 {@code ACTIVE}）
     * @return 匹配的位置轨迹行
     */
    List<EncounterLocation> findAllByLocationIdInAndFormEnumAndStatusEnum(
        Collection<Long> locationIds, String formEnum, String statusEnum);
}

