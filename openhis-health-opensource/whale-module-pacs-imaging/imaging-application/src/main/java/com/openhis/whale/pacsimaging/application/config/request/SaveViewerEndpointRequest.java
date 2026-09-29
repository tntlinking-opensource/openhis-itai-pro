/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存阅片端点请求。
 */
@Getter
@Setter
public class SaveViewerEndpointRequest {

    /** 主键；空则新增。 */
    private Long id;

    /** 机构主键；空=全局。 */
    private Long orgId;

    /** 模态码；空=该范围默认。 */
    private String modalityCode;

    /** OHIF 根地址（http/https）。 */
    @NotBlank
    private String baseUrl;

    /** 是否启用。 */
    @NotNull
    private Boolean enabled;
}
