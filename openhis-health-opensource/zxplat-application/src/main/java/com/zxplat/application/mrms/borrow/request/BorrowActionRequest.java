/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.borrow.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 借阅审批/拒绝入参。
 */
@Getter
@Setter
public class BorrowActionRequest {

    /** 借阅申请主键。 */
    private Long borrowRequestId;

    /** 审批意见或拒绝原因。 */
    private String comment;
}
