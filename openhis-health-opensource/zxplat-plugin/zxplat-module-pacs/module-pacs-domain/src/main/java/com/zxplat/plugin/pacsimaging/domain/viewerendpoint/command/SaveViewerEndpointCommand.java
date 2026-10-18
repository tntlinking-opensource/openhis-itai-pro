/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.viewerendpoint.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存阅片端点命令。
 */
@Getter
@Setter
public class SaveViewerEndpointCommand {

    /** 主键；空则新增。 */
    private Long id;

    /** 机构标识；可选。 */
    private Long organizationId;

    /** 模态码；可选，空白视为空。 */
    private String modalityCode;

    /** OHIF 根地址。 */
    private String baseUrl;

    /** 是否启用。 */
    private Boolean enabled;

    /** 租户主键。 */
    private Long tenantId;

    /** 操作人主键。 */
    private Long operatorId;
}
