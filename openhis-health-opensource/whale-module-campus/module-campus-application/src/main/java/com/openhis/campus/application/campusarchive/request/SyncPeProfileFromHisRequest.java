/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusarchive.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncPeProfileFromHisRequest {

    private Long tenantId;

    @NotBlank
    private String studentNo;

    private String name;
    private String idCard;

    @NotBlank
    private String cardNo;

    @NotBlank
    private String gfStatus;
}
