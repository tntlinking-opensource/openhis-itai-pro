/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.diagnosis.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 诊断建议库分页检索请求（CFG-08）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchDiagnosisTemplateRequest extends AbstractHealthPageRequest {

    /** 诊断来源分类；服务端固定为 peis_dx（体检诊断），入参忽略。 */
    private String sourceEnum;

    /** 状态精确过滤。 */
    private String statusEnum;
}
