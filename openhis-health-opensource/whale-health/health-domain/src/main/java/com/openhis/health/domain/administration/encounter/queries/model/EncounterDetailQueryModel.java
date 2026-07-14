/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊详情读模型：头信息含类别、状态、机构与时间轴。
 */
@Getter
@Setter
public class EncounterDetailQueryModel {
    /** 就诊主键。 */
    private Long id;

    /** 就诊业务号（住院号等）。 */
    private String busNo;
    /** 患者主键。 */
    private Long patientId;
    /** 就诊类别枚举编码。 */
    private String classEnum;
    /** 就诊状态枚举编码。 */
    private String statusEnum;
    /** 机构主键。 */
    private Long organizationId;
    /** 就诊开始时间。 */
    private LocalDateTime startTime;
    /** 就诊结束时间。 */
    private LocalDateTime endTime;
    /** 接诊时间。 */
    private LocalDateTime receptionTime;

    /** 入院来源编码。 */
    private String admitSourceCode;

    /** 病情优先级编码。 */
    private String priorityEnum;

    /** 源门诊就诊主键（住院线 {@code adm_encounter.amb_encounter_id}；无申请直登时为 null）。 */
    private Long ambEncounterId;
}
