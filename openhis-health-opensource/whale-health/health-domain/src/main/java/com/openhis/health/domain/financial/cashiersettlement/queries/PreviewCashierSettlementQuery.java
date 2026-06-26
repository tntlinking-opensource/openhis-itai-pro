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

package com.openhis.health.domain.financial.cashiersettlement.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 未交账 payment 预览查询条件。
 */
@Getter
@Setter
public class PreviewCashierSettlementQuery {

    /** 交账类型：OUTPATIENT / INPATIENT。 */
    private String typeEnum;

    /** 经办人（enterer 过滤）。 */
    private Long cashierId;

    /**
     * payment {@code enterer_id} 匹配白名单（Practitioner 主键 + 绑定用户主键）；由应用层写入。
     */
    private List<Long> entererMatchIds;

    /** 业务日起。 */
    private LocalDate dateFrom;

    /** 业务日止。 */
    private LocalDate dateTo;
}
