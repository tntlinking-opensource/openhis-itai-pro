/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.chargeitem.command;

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
