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

package com.openhis.health.domain.share.enums;

/**
 * 就诊参与者角色（{@code adm_encounter_participant.role_code}，DM-ADM-002 §3.2.1）。
 *
 * <p>持久化与事件载荷一律存 {@link #getCode()} 字符串身份。</p>
 */
public enum EncounterParticipantRole implements EnumInterface {

    /** 挂号时指定的受理医生。 */
    REGISTRATION_DOCTOR(0L, "REGISTRATION_DOCTOR", "挂号医生", "挂号时指定的受理医生", 1L),

    /** 实际接诊的医生。 */
    ADMITTER(0L, "ADMITTER", "接诊医生", "实际接诊的医生", 2L),

    /** 负责治疗的主治医生。 */
    ATTENDING_DOCTOR(0L, "ATTENDING_DOCTOR", "主治医生", "负责治疗的主治医生", 3L),

    /** 上级查房/会诊主任。 */
    CHIEF_DOCTOR(0L, "CHIEF_DOCTOR", "主任医生", "上级查房/会诊主任", 4L),

    /** 住院责任护士。 */
    PRIMARY_NURSE(0L, "PRIMARY_NURSE", "责任护士", "住院责任护士", 5L),

    /** 门诊分诊护士。 */
    TRIAGE_NURSE(0L, "TRIAGE_NURSE", "分诊护士", "门诊分诊护士", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    EncounterParticipantRole(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
