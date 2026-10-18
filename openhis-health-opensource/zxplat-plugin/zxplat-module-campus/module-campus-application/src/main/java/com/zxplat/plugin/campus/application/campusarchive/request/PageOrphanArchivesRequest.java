/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusarchive.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageOrphanArchivesRequest {

    private Long tenantId;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
