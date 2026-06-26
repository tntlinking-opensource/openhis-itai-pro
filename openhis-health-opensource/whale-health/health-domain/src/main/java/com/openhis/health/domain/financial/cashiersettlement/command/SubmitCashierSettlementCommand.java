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

package com.openhis.health.domain.financial.cashiersettlement.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 提交收费员交账命令。
 */
@Getter
@Setter
public class SubmitCashierSettlementCommand {

    /** 交账类型：OUTPATIENT / INPATIENT。 */
    private String typeEnum;

    /** 交账所属科室。 */
    private Long orgId;

    /** 收费窗口，可空。 */
    private Long locationId;

    /** 交账经办人（默认纳入 enterer_id 等于该值的 payment）。 */
    private Long cashierId;

    /**
     * payment {@code enterer_id} 匹配白名单（Practitioner 主键 + 绑定用户主键）；提交锁单与预览共用。
     */
    private List<Long> entererMatchIds;

    /** 业务日起（查询窗，不落库）。 */
    private LocalDate dateFrom;

    /** 业务日止（查询窗，不落库）。 */
    private LocalDate dateTo;

    /** 当前操作人（审计）。 */
    private Long operatorId;
}
