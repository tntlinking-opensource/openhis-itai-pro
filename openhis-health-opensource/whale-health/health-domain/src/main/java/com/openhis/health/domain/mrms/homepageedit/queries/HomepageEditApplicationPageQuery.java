/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.homepageedit.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 首页编辑申请分页查询入参。
 */
@Getter
@Setter
public class HomepageEditApplicationPageQuery {

    /** 租户。 */
    private Long tenantId;
    /** 状态 code；空表示不限。 */
    private String statusEnum;
    /** 病案主键；空表示不限。 */
    private Long caseRecordId;
    /** 页码（从 1）。 */
    private Integer pageIndex;
    /** 页大小。 */
    private Integer pageSize;
}
