/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * 关闭待办写结果。
 */
@Data
public class CloseNoticeTodoResult {
    /** 待办主键。 */
    private Long todoId;
    /** 关闭后状态。 */
    private String status;
}
