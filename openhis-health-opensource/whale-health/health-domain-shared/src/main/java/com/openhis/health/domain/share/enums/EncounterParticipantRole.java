/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

/** 接诊医生（EncounterParticipantRole）。 */
    ADMITTER(0L, "ADMITTER", "接诊医生", "实际接诊的医生", 2L),

    /** 负责治疗的主治医生。 */
    ATTENDING_DOCTOR(0L, "ATTENDING_DOCTOR", "主治医生", "负责治疗的主治医生", 3L),

    /** 上级查房/会诊主任。 */
    CHIEF_DOCTOR(0L, "CHIEF_DOCTOR", "主任医生", "上级查房/会诊主任", 4L),

/** 责任护士（EncounterParticipantRole）。 */
    PRIMARY_NURSE(0L, "PRIMARY_NURSE", "责任护士", "住院责任护士", 5L),

/** 分诊护士（EncounterParticipantRole）。 */
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
