/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建文书编辑申请入参。
 */
@Getter
@Setter
public class EmrDocumentEditCreateExtensionIn {

    /** 租户。 */
    private Long tenantId;

    /** 文书主键。 */
    private Long documentId;

    /** 门诊/住院 class（AMB/IMP）。 */
    private String encounterClassCode;

    /** 申请原因。 */
    private String applyReason;

    /** 申请人。 */
    private Long applicantId;

    /** 操作人。 */
    private Long operatorId;
}
