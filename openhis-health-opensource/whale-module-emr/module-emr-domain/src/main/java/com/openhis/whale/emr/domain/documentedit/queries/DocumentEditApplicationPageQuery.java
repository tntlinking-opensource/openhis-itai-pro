/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.documentedit.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 文书编辑申请分页查询入参（DM-EMR-002）。
 */
@Getter
@Setter
public class DocumentEditApplicationPageQuery {

    /** 租户。 */
    private Long tenantId;

    /** 状态 code；空表示不限。 */
    private String statusEnum;

    /** 文书主键；空表示不限。 */
    private Long documentId;

    /** 门诊/住院 class；空表示不限。 */
    private String encounterClassCode;

    /** 页码（从 1）。 */
    private Integer pageIndex;

    /** 页大小。 */
    private Integer pageSize;
}
