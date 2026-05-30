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

package com.openhis.health.domain.clinical.allergyintolerance.repository;

import com.openhis.health.domain.clinical.allergyintolerance.entity.AllergyIntolerance;
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
}
