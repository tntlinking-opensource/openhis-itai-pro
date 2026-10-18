/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetTemplateContentRequest {

    /** 模板主键 id */
    private String templateId;
}
