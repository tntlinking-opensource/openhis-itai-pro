/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.queries;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 检验项目对照分页查询条件。
 */
@Getter
@Setter
@Accessors(chain = true)
public class LabItemMapPageCriteria {

    /** 租户 id。 */
    private Long tenantId;

    /** 关键字（匹配业务码/名称/国家码）。 */
    private String keyword;

    /** 仅未对照（国家码为空）。 */
    private Boolean unmappedOnly;
}
