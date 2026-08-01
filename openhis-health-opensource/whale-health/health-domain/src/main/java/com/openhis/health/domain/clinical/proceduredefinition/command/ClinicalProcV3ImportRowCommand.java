/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClinicalProcV3ImportRowCommand {
    private String procedureCode;
    private String name;
    /** 类别（Excel「类别」列，写入 typeCode）。 */
    private String typeCode;
}
