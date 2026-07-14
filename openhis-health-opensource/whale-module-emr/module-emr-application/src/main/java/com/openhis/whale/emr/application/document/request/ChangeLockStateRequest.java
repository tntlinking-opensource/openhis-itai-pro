/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeLockStateRequest {

    /** 文书主键 id */
    private Long documentId;
    /**
     * 操作类型：LOCK / UNLOCK
     */
    private String action;
    /** reason（业务字段） */
    private String reason;
}
