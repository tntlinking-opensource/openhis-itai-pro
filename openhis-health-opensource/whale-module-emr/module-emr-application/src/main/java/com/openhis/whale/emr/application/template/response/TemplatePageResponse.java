/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.template.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 模板分页响应
 */
@Getter
@Setter
public class TemplatePageResponse {

    /** 当前页模板列表（含 content） */
    private List<TemplateDto> content;
    /** 总记录数 */
    private Long totalCount;
}
