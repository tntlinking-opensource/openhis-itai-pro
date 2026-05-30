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

package com.openhis.health.domain.emergency.patientfocus.repository;

import com.openhis.health.domain.emergency.patientfocus.entity.EmgPatientFocus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 患者重点关注仓储。
 */
public interface EmgPatientFocusRepository extends JpaRepository<EmgPatientFocus, Long> {

    Optional<EmgPatientFocus> findByEncounterIdAndStatusEnumAndIsDeletedFalse(Long encounterId, String statusEnum);

    List<EmgPatientFocus> findAllByEncounterIdInAndStatusEnumAndIsDeletedFalse(
        Collection<Long> encounterIds, String statusEnum);
}
