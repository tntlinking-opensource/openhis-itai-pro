/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书编辑申请驳回入参。
 */
@Getter
@Setter
public class EmrDocumentEditRejectExtensionIn {

    /** 申请主键。 */
    private Long applicationId;

    /** 驳回原因。 */
    private String rejectReason;

    /** 审核人。 */
    private Long operatorId;
}
