/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.response;

import lombok.Data;

/**
 * 关闭待办响应（IF-CHS-CAT-005-W-04）。
 */
@Data
public class CloseNoticeTodoResponse {
    /** 已关闭的待办主键。 */
    private Long todoId;
    /** 关闭后状态（通常 CLOSED）。 */
    private String status;
}
