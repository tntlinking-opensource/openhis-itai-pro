/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 编辑前可编辑性校验查询模型。
 */
@Getter
@Setter
public class ActivityEditValidationQueryModel {
    /** editable。 */
    private Boolean editable;
    /** 原因编码。 */
    private String reasonCode;
    /** 原因message。 */
    private String reasonMessage;
}
