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

package com.openhis.health.domain.administration.encounter.command;

import lombok.Data;

/**
 * 住院出院财务全结命令：将就诊由待结算推进为已结算（DM-ADM-002 §6.1.23）。
 */
@Data
public class CompleteInpatientFinancialSettlementCommand {

    /** 住院就诊主键。 */
    private Long encounterId;

    /** 结算业务时间（可选；缺省由领域服务取当前时间）。 */
    private java.time.LocalDateTime settlementTime;

    /** 操作员主键（可选；审计由应用层补充）。 */
    private Long operatorId;
}
