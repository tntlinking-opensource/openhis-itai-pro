/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 3101 手术节点（fsi_operation_dtos 元素；W1 可空列表） */
@Data
@Accessors(chain = true)
public class AuditOperationCommand {

    /** 手术操作 ID */
    private String setlListOprnId;
    /** 手术操作代码 */
    private String oprnCode;
    /** 手术操作名称 */
    private String oprnName;
    /** 主手术标志 */
    private String mainOprnFlag;
    /** 手术日期 */
    private String oprnDate;
}
