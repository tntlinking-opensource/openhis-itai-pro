/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * 按执行记录主键逻辑删除长期医嘱执行新建待结算收费明细（AS-IP-NUR-012 BR-IP-CXL-008）。
 */
@Getter
@Setter
public class VoidOrderExecuteChargeItemsByProcedureIdsCommand {

    /** 操作人主键。 */
    private Long operatorId;

    /** 执行记录主键集合（Procedure.id）。 */
    private Collection<Long> procedureIds;
}
