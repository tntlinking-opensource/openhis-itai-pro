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
 * 从 {@code adm_encounter} 与参与者组装，映射首页住院/人员字段。</p>
 */
@Getter
@Setter
public class EncounterSnapshot {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;

    /** 患者主键 {@code adm_encounter.patient_id}。 */
    private Long patientId;

    /** 住院号（4401 关联住院号，来自 {@code adm_encounter.bus_no}）。 */
    private String iptNo;

    /** 入院时间（4401 adm_time，来自 {@code adm_encounter.start_time}）。 */
    private LocalDateTime admTime;

    /** 出院时间（4401 dscg_time，来自 {@code adm_encounter.end_time}）。 */
    private LocalDateTime dscgTime;

    /** 入院科别编码（4401 adm_caty，机构 {@code bus_no}）。 */
    private String admCatyCode;

    /** 入院科别名称（4401 adm_caty 展示名）。 */
    private String admCatyName;

    /** 出院科别编码（4401 dscg_caty，末次病区机构 {@code bus_no}）。 */
    private String dscgCatyCode;

    /** 出院科别名称（4401 dscg_caty 展示名）。 */
    private String dscgCatyName;

    /** 科主任姓名（4401 dept_director；Wave1 参与者映射预留）。 */
    private String deptDirectorName;

    /** 主任/副主任医师姓名（4401 chfdr，参与者角色 chief_doctor）。 */
    private String chfdrName;

    /** 主治医师姓名（4401 atddr，参与者角色 attending_doctor）。 */
    private String atddrName;

    /** 主治医师 {@code adm_practitioner.id}（列表过滤与冗余同步用）。 */
    private Long atddrPractitionerId;

    /** 住院医师姓名（4401 ipt_dr，参与者角色 admitter）。 */
    private String iptDrName;

    /** 责任护士姓名（4401 resp_nurs，参与者角色 primary_nurse）。 */
    private String respNursName;
}
