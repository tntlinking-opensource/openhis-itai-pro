/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcedureDefinitionDetailQueryModel {
    private Long id;
    private String procedureCode;
    private String name;
    private String sourceEnum;
    private String pyStr;
    private String wbStr;
    private String typeCode;
    private String description;
    private String ybNo;
    private Boolean ybFlag;
    private Boolean ybMatchFlag;
    private String clinicCode;
    private String statusEnum;
}
