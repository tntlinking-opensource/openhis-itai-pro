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

package com.openhis.health.domain.print.printlog.queries;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 打印审计日志分页/日汇总扫描共用筛选条件（DM-PRT-002）。
 */
@Getter
@Setter
public class PrtPrintEventLogPageQuery {

    /** 租户（Gate 注入）。 */
    private Long tenantId;

    /** 模板 busNo；可空。 */
    private String busNo;

    /** 打印结果状态码（{@link com.openhis.health.domain.share.enums.PrintLogStatus}）；可空。 */
    private String status;

    /** 创建日起；可空。 */
    private LocalDate dateFrom;

    /** 创建日止（含当日）；可空。 */
    private LocalDate dateTo;
}
