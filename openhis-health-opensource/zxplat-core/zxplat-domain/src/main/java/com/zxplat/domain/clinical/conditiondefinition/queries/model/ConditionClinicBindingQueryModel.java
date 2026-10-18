/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 医保码反查国临对照展示（一对多：多个国临可绑同一医保码）。
 *
 * <p>真源为国临目录 {@code yb_no}；本模型仅用于列表逗号拼接回显。</p>
 */
@Getter
@Setter
public class ConditionClinicBindingQueryModel {

    /** 国临码，多个时英文逗号拼接；无对照为空。 */
    private String clinicCodes;

    /** 国临诊断名称，多个时英文逗号拼接；无对照为空。 */
    private String clinicNames;
}
