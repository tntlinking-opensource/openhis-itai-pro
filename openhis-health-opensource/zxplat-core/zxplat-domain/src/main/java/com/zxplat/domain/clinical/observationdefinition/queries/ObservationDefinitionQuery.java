/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observationdefinition.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 临床观察定义分页检索条件（CFG-03 列表）。
 */
@Getter
@Setter
public class ObservationDefinitionQuery {

    /** 租户标识；空则不按租户收窄（一般由应用层传入）。 */
    private Long tenantId;

    /** 关键字：匹配 code / display（不区分大小写）。 */
    private String keyword;

    /** 分类编码精确过滤。 */
    private String categoryCode;

    /** 状态精确过滤。 */
    private String statusEnum;
}
