/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书编辑申请分页入参。
 */
@Getter
@Setter
public class EmrDocumentEditPageExtensionIn {

    /** 租户。 */
    private Long tenantId;

    /** 状态；空不限。 */
    private String statusEnum;

    /** 文书主键；空不限。 */
    private Long documentId;

    /** AMB/IMP；空不限。 */
    private String encounterClassCode;

    /** 页码从 1。 */
    private Integer pageIndex;

    /** 页大小。 */
    private Integer pageSize;
}
