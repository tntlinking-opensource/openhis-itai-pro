/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊疗项目维护页初始化查询模型。
 */
@Getter
@Setter
public class ActivityDefinitionInitQueryModel {
    /** 类别options。 */
    private List<OptionDto> categoryOptions;
    /** unitoptions。 */
    private List<OptionDto> unitOptions;
    /** 类型options。 */
    private List<OptionDto> typeOptions;
    /** 状态options。 */
    private List<OptionDto> statusOptions;
}
