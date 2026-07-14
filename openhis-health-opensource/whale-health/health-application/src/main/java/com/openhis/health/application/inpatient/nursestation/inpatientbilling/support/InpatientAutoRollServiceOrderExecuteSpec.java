/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 住院滚费长期诊疗医嘱单槽位执行命令（AS-IP-NUR-013 IF-IP-ARG-08；对齐 IF-IP-EXE-02）。
 */
@Getter
@Setter
public class InpatientAutoRollServiceOrderExecuteSpec {

    /** 待执行诊疗申请主键。 */
    private Long serviceRequestId;

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 个人账户主键。 */
    private Long accountId;

    /** 机构主键（orgId / requestingOrgId）。 */
    private Long organizationId;

    /** 滚费业务日（planTime = billDate 00:00:00）。 */
    private LocalDate billDate;

    /** 执行数量。 */
    private BigDecimal quantity;

    /** 数量单位持久化码。 */
    private String quantityUnit;

    /** 优先定价 id（床位配置锚定；护理可为 null 走目录择优）。 */
    private Long preferredDefinitionId;

    /** 主执行人（责任护士；写入 Procedure performer 与 Charge enterer）。 */
    private Long performerId;

    /** 任务/系统操作人（Procedure operator）。 */
    private Long operatorId;
}
