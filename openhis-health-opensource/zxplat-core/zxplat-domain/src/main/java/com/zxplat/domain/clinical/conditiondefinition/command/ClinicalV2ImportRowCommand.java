/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 国家临床版 2.0 单行导入命令（编码 + 名称）。
 */
@Getter
@Setter
public class ClinicalV2ImportRowCommand {
    /** 国临主要编码。 */
    private String conditionCode;
    /** 疾病名称。 */
    private String name;
}
