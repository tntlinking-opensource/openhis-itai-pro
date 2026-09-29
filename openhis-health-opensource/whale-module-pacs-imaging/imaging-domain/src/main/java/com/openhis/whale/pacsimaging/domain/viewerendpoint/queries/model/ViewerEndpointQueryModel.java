/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * ViewerEndpoint 读模型。
 */
@Getter
@Setter
public class ViewerEndpointQueryModel {

    /** 主键。 */
    private Long id;

    /** 机构标识。 */
    private Long organizationId;

    /** 模态码。 */
    private String modalityCode;

    /** 阅片根地址。 */
    private String baseUrl;

    /** 是否启用。 */
    private Boolean enabled;

    /**
     * 匹配来源；仅 {@code resolve} 填充：
     * {@code ORG_MODALITY} / {@code ORG_DEFAULT} / {@code GLOBAL}。
     */
    private String matchedBy;
}
