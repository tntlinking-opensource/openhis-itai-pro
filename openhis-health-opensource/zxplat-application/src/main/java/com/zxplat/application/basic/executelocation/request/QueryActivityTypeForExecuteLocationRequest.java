/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.executelocation.request;

import lombok.Data;

/**
 * 按诊疗大类拉取业务类型下拉：仅返回该大类目录项目实际使用过的 {@code type_enum}。
 */
@Data
public class QueryActivityTypeForExecuteLocationRequest {

    /** 诊疗大类码（术语 {@code activity_category_code}），必填。 */
    private String activityCategoryCode;
}
