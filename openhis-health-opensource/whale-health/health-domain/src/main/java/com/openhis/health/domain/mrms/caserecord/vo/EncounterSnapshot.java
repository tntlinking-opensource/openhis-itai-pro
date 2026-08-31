/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊读模型快照（自动取值中间结构，非 QueryModel）。
 *
 * <p>由 {@link com.openhis.health.domain.mrms.caserecord.support.CaseRecordAutoFillSupport#loadEncounter}
 * 从就诊、位置轨迹、参与者组装。</p>
 */
@Getter
@Setter
public class EncounterSnapshot {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;

    /** 患者主键 {@code adm_encounter.patient_id}。 */
    private Long patientId;

    /** 住院号（{@code adm_encounter.bus_no}）。 */
    private String iptNo;

    /** 入院时间（{@code start_time}）。 */
    private LocalDateTime admTime;

    /**
     * 业务出院时间：优先出院医嘱计划时间 {@code occurrence_start_time}，
     * 回落 {@code Encounter.endTime}（办理出院写入；非清床/结算时刻）。
     */
    private LocalDateTime dscgTime;

    /** 入院科别编码（机构 bus_no）。 */
    private String admCatyCode;

    /** 入院科别名称。 */
    private String admCatyName;

    /** 出院科别编码。 */
    private String dscgCatyCode;

    /** 出院科别名称。 */
    private String dscgCatyName;

    /** 入院途径字典码（4401 adm_way；与登记 in_way_code 同口径）。 */
    private String admWayCode;

    /** 入院病区名称（最早 WARD 轨迹）。 */
    private String admWardName;

    /** 入院床位名称（最早 BED 轨迹）。 */
    private String admBedName;

    /** 出院/当前病区名称（末次完成或当前活动 WARD）。 */
    private String dscgWardName;

    /** 出院/当前床位名称（末次完成或当前活动 BED）。 */
    private String dscgBedName;

    /** 科主任姓名。 */
    private String deptDirectorName;

    /** 主任/副主任医师姓名。 */
    private String chfdrName;

    /** 主治医师姓名。 */
    private String atddrName;

    /** 主治医师主键。 */
    private Long atddrPractitionerId;

    /** 住院医师姓名。 */
    private String iptDrName;

    /** 责任护士姓名。 */
    private String respNursName;
}
