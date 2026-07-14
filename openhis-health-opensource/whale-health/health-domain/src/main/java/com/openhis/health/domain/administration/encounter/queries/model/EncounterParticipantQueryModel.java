/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊参与者读模型：医师/护士等角色在某就诊上的参与时段。
 */
@Getter
@Setter
public class EncounterParticipantQueryModel {
    /** 参与者记录主键。 */
    private Long participantId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 参与者角色编码。 */
    private String roleCode;
    /** 医疗卫生人员主键。 */
    private Long practitionerId;
    /** 参与状态枚举编码。 */
    private String statusEnum;
    /** 参与开始时间。 */
    private LocalDateTime startTime;
    /** 参与结束时间。 */
    private LocalDateTime endTime;
}
