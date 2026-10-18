/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstaff.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveStaffPeProfileRequest {

    private Long tenantId;

    @NotNull
    private Long staffId;

    private String cardNo;
    private String medicalCode;
    private String gfStatusEnum;
    private String categoryEnum;
    private Boolean eligibleFlag;
    private String remark;
}
