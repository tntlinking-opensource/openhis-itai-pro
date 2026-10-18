/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printlog.queries;

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

    /** 打印结果状态码（{@link com.zxplat.domain.share.enums.PrintLogStatus}）；可空。 */
    private String status;

    /** 创建日起；可空。 */
    private LocalDate dateFrom;

    /** 创建日止（含当日）；可空。 */
    private LocalDate dateTo;
}
