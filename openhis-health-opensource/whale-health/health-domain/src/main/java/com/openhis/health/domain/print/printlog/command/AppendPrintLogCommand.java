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

package com.openhis.health.domain.print.printlog.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 追加打印审计日志命令（TC-PRT-LOG-APPEND-001）。
 */
@Getter
@Setter
public class AppendPrintLogCommand {

    /** 租户主键。 */
    private Long tenantId;

    /** 机构主键。 */
    private Long organizationId;

    /** 打印链路 traceId。 */
    private String traceId;

    /** 模板 busNo。 */
    private String busNo;

    /** 场景 bindingCode。 */
    private String bindingCode;

    /** 打印时模板 version 快照。 */
    private Integer templateVersion;

    /** 打印结果（SUCCESS / FAILED）。 */
    private String status;

    /** 打印机名称。 */
    private String printerName;

    /** 打印份数。 */
    private Integer copies;

    /** 打印内容哈希。 */
    private String contentHash;

    /** 失败错误码。 */
    private String errorCode;

    /** 失败错误信息。 */
    private String errorMessage;

    /** 业务引用 JSON。 */
    private String bizRefJson;

    /** 客户端类型；缺省 {@code openhis-win}。 */
    private String clientType;

    /** 操作人主键。 */
    private Long operatorId;

    /** 操作人姓名快照。 */
    private String operatorName;
}
