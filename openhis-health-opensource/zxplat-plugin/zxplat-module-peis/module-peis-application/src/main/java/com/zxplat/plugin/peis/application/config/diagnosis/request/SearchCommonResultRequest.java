/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.diagnosis.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 常见结果分页检索请求（CFG-09）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchCommonResultRequest extends AbstractHealthPageRequest {

    /** 绑定小项观察定义主键。 */
    private Long observationDefinitionId;

    /** 科室机构主键。 */
    private Long deptOrgId;

    /** 状态精确过滤。 */
    private String statusEnum;
}
