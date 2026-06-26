/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.snapshot;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊读模型快照（自动取值用）。
 */
@Getter
@Setter
public class EncounterSnapshot {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 住院号/业务号。 */
    private String iptNo;

    /** 入院时间。 */
    private LocalDateTime admTime;

    /** 出院时间。 */
    private LocalDateTime dscgTime;

    /** 入院科别编码。 */
    private String admCatyCode;

    /** 入院科别名称。 */
    private String admCatyName;

    /** 出院科别编码。 */
    private String dscgCatyCode;

    /** 出院科别名称。 */
    private String dscgCatyName;

    /** 科主任姓名。 */
    private String deptDirectorName;

    /** 主任/副主任医师。 */
    private String chfdrName;

    /** 主治医师。 */
    private String atddrName;

    /** 主治医师 {@code adm_practitioner.id}。 */
    private Long atddrPractitionerId;

    /** 住院医师。 */
    private String iptDrName;

    /** 责任护士。 */
    private String respNursName;
}
