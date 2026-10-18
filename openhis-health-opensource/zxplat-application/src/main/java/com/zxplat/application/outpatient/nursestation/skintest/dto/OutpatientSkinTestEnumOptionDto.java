/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.skintest.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 皮试表单枚举下拉项。
 */
@Getter
@Setter
public class OutpatientSkinTestEnumOptionDto {

    /** 枚举 code。 */
    private String code;

    /** 展示文案。 */
    private String displayText;
}
