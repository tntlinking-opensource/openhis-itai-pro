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

package com.openhis.whale.emr.domain.metadata.port;

import java.util.Map;

/**
 * 向元数据解析上下文贡献患者/就诊等外部数据（由 health 等模块实现）。
 */
public interface EmrMetadataContextPort {

    /**
     * 写入扁平路径键值，如 {@code patient.name}、{@code patient.phone}。
     */
    void contributePatient(Map<String, String> target, String patientId);

    /**
     * 写入扁平路径键值，如 {@code encounter.deptName}。
     */
    void contributeEncounter(Map<String, String> target, String encounterId);

    /**
     * 写入医务人员主档扁平键，如 {@code practitioner.name}、{@code practitioner.signatureImage}。
     *
     * @param target 上下文 Map
     * @param practitionerId {@code adm_practitioner.id}；无效时静默跳过
     */
    void contributePractitioner(Map<String, String> target, Long practitionerId);
}
