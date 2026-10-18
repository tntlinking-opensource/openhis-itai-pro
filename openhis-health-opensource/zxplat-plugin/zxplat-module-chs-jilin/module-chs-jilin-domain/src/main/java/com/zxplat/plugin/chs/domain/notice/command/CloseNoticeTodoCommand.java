/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.command;

import lombok.Data;

/**
 * 关闭催办待办写命令（W-04）。
 */
@Data
public class CloseNoticeTodoCommand {
    /** 租户主键。 */
    private Long tenantId;
    /** 待办主键。 */
    private Long todoId;
    /** 关闭原因（必填）。 */
    private String closeReason;
    /** 操作者；可空。 */
    private Long operatorId;
}
