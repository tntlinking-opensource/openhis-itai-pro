/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 挂号单查询行（替代 {@code RegistrationSlipReadModel}）。
 */
@Getter
@Setter
public class RegistrationSlipQueryModel {

    /** 挂号单号/slip 业务号。 */
    private String slipNo;
    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;
    /** 患者姓名（快照）。 */
    private String patientName;
    /** 挂号科室名称。 */
    private String organizationName;
    /** 挂号医生姓名。 */
    private String doctorName;
    /** 号别/服务类型名称。 */
    private String serviceTypeName;
    /** 就诊状态展示文案。 */
    private String statusEnumText;
    /** 挂号费金额；由应用层按收费明细回填时可 null。 */
    private BigDecimal amount;
    /** 打印次数；主表未持久化时为 null（降级）。 */
    private Integer printCount;
}
