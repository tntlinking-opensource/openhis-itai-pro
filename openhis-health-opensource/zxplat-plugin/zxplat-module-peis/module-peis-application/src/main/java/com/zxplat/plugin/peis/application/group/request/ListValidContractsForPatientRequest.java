/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询患者有效团检合同请求。
 */
@Getter
@Setter
public class ListValidContractsForPatientRequest {

    /** 患者主键。 */
    @NotNull
    private Long patientId;
}
