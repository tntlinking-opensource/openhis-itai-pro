/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书编辑申请 id + 操作人入参（交审/同意/查询）。
 */
@Getter
@Setter
public class EmrDocumentEditIdExtensionIn {

    /** 申请主键。 */
    private Long applicationId;

    /** 操作人/审核人。 */
    private Long operatorId;
}
