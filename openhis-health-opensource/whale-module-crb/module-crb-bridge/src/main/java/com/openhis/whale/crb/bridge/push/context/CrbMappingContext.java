/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.context;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Mapper 运行时上下文（adapter 内构造，每次 map 注入）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class CrbMappingContext {

    /** 国家机构代码。 */
    private String orgCode;

    /** 机构名称。 */
    private String orgName;

    /** 当前租户 id。 */
    private Long tenantId;

    /** 操作人 id 字符串。 */
    private String operatorId;
}
