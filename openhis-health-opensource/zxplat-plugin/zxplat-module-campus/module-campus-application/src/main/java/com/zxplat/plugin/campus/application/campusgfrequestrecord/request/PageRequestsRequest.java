/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfrequestrecord.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageRequestsRequest {

    private Long tenantId;
    private String chargeModeEnum;
    private String statusEnum;
    private String keyword;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
