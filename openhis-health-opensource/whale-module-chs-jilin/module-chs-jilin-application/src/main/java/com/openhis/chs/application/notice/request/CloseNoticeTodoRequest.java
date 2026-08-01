/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.request;

import lombok.Data;

/**
 * 关闭催办待办请求（IF-CHS-CAT-005-W-04）。
 */
@Data
public class CloseNoticeTodoRequest {
    /** 待办主键（必填）。 */
    private Long todoId;
    /** 关闭原因（必填，写入审计）。 */
    private String closeReason;
    /** 操作者用户主键；可空。 */
    private Long operatorId;
}
