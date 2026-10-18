/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.borrow.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 标记借出/归还入参。
 */
@Getter
@Setter
public class BorrowLifecycleRequest {

    /** 借阅申请主键。 */
    private Long borrowRequestId;

    /** 应归还时间（标记借出时填写）。 */
    private LocalDateTime dueReturnAt;
}
