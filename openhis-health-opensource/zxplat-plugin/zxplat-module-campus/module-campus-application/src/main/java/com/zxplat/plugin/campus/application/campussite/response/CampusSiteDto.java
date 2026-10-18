/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campussite.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampusSiteDto {

    private Long id;
    private String campusCode;
    private String campusName;
    private String shortName;
    private String address;
    private Boolean isPrimary;
    private Boolean activeFlag;
    private Integer sortOrder;
    private String remark;
}
