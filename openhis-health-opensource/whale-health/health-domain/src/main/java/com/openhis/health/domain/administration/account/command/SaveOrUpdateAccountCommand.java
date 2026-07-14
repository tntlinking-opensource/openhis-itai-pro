/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.account.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 保存或更新账户命令：维护就诊下某类型账户余额与默认标志（由领域服务做存在性分支）。
 */
@Getter
@Setter
public class SaveOrUpdateAccountCommand {
    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 账户类型编码。 */
    private String typeCode;
    /** 账户余额。 */
    private BigDecimal balance;
    /** 是否默认账户。 */
    private Boolean defaultFlag;
    /** 操作人主键。 */
    private Long operatorId;
}
