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

package com.openhis.health.domain.administration.patient.repository;

import com.openhis.health.domain.administration.patient.entity.PatientIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 患者标识子实体仓储。
 */
public interface PatientIdentifierRepository extends JpaRepository<PatientIdentifier, Long> {

    /**
     * 查询患者有效标识集合。
     *
     * @param patientId 患者标识
     * @return 标识列表
     */
    List<PatientIdentifier> findAllByPatientId(Long patientId);

    /**
     * 批量查询患者有效标识集合。
     *
     * @param patientIds 患者标识集合
     * @return 标识列表
     */
    List<PatientIdentifier> findAllByPatientIdIn(Collection<Long> patientIds);

    /**
     * 判断跨患者标识号是否冲突。
     *
     * @param typeCode 证件类型
     * @param identifierNo 标识号码
     * @param patientId 患者标识
     * @return 存在冲突返回 true
     */
    boolean existsByTypeCodeAndIdentifierNoAndPatientIdNot(String typeCode, String identifierNo, Long patientId);

    /**
     * 按证件类型与证件号集合查询未删除标识。
     *
     * @param typeCodes 证件类型集合
     * @param identifierNos 证件号集合
     * @return 标识实体列表
     */
    List<PatientIdentifier> findAllByTypeCodeInAndIdentifierNoIn(Collection<String> typeCodes,
                                                                                   Collection<String> identifierNos);

    /**
     * 按患者和子实体主键查询。
     *
     * @param id 子实体标识
     * @param patientId 患者标识
     * @return 子实体
     */
    Optional<PatientIdentifier> findByIdAndPatientId(Long id, Long patientId);
}
