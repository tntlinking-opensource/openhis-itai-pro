/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 国临手术 3.0 目录导入行。
 */
@Getter
@Setter
public class ClinicalProcV3ImportRowCommand {

    /** 国临主码（{@code procedure_code} / {@code clinic_code}）。 */
    private String procedureCode;

    /** 术式名称。 */
    private String name;

    /** 类别（Excel「类别」列，写入 typeCode）。 */
    private String typeCode;
}
