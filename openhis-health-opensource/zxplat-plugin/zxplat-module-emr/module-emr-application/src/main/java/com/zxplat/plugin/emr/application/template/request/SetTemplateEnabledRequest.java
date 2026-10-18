/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 模板启停请求
 */
@Getter
@Setter
public class SetTemplateEnabledRequest {

    /** 模板主键 id */
    private String templateId;
    /** 是否启用 */
    private Boolean enabled;
}
