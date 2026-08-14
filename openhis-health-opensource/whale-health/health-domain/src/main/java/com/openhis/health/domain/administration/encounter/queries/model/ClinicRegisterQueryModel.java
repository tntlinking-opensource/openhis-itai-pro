/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊挂号列表行（替代 {@code ClinicRegisterReadModel}）。
 */
@Getter
@Setter
public class ClinicRegisterQueryModel {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;
    /** 就诊业务号/挂号流水号。 */
    private String encounterBusNo;
    /** 患者姓名（快照）。 */
    private String patientName;
    /** 患者性别字典码。 */
    private String patientGenderCode;
    /** 患者性别展示文案（字典翻译，只读）。 */
    private String patientGenderText;
    /** 患者年龄展示文案（由出生日期推算，如 45岁）。 */
    private String patientAgeText;
    /** 联系电话（快照）。 */
    private String contactPhone;
    /** 挂号科室名称。 */
    private String organizationName;
    /** 挂号医生姓名。 */
    private String doctorName;
    /** 就诊状态持久化码。 */
    private String statusEnum;
    /** 就诊状态展示文案。 */
    private String statusEnumText;
    /** 挂号/就诊开始时刻。 */
    private LocalDateTime startTime;
    /** 号别/服务类型主键。 */
    private Long serviceTypeId;
    /** 号别/服务类型名称。 */
    private String serviceTypeName;
    /** 打印次数；主表未持久化时为 null（降级）。 */
    private Integer printCount;
}
