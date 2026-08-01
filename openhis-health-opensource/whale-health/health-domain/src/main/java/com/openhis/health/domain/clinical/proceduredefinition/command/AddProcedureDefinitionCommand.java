/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.command;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddProcedureDefinitionCommand {
    private String sourceEnum;
    private String procedureCode;
    private String name;
    private String pyStr;
    private String wbStr;
    private String typeCode;
    private String description;
    private String statusEnum;
    private Boolean ybFlag;
    private String ybNo;
    private Boolean ybMatchFlag;
    private String clinicCode;
}
