/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书归档写操作出参。
 */
@Getter
@Setter
public class EmrDocumentArchiveWriteExtensionOut {

    /** 是否成功。 */
    private boolean success;

    /** 提示信息。 */
    private String message;

    /** 文书主键。 */
    private Long documentId;

    /** 写后状态 code。 */
    private String statusCode;

    /** 业务版本号。 */
    private Integer versionNo;
}
