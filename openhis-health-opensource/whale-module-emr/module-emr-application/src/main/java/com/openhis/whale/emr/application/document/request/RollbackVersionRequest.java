/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RollbackVersionRequest {

    /** 文书主键 id */
    private Long documentId;
    /** 版本记录主键 id */
    private Long versionId;
    /** 版本序号（从 1 递增） */
    private Integer versionNumber;
}
