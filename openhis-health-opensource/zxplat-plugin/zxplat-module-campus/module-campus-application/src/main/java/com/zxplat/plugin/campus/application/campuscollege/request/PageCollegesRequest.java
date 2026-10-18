/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campuscollege.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageCollegesRequest {

    private Long tenantId;
    private String keyword;
    private Boolean activeFlag;
    private Long campusSiteId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
