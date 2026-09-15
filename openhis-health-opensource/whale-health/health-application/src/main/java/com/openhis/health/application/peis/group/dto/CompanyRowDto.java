/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位行 DTO。
 */
@Getter
@Setter
public class CompanyRowDto {

    private Long companyId;
    private String name;
    private Long parentId;
    private Long orgId;
    private Long campusCollegeId;
    private String contactName;
    private String contactPhone;
}
