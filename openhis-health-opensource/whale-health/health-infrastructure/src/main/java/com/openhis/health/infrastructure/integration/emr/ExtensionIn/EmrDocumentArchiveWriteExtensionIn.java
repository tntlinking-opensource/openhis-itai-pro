/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书归档写操作入参（提交/同意/驳回）。
 */
@Getter
@Setter
public class EmrDocumentArchiveWriteExtensionIn {

    /** 文书主键。 */
    private Long documentId;

    /** 操作人（提交人或审批人）。 */
    private Long operatorId;

    /** 驳回原因；仅驳回必填。 */
    private String rejectReason;
}
