/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.peis.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 小程序查询可约时段请求。
 */
@Getter
@Setter
public class MiniappListHcBookableSlotsRequest {

    /** 会话患者（可空，由 BFF 回填）。 */
    private Long patientId;

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 计划日。 */
    @NotNull
    private LocalDate planDate;

    /** 合同类型编码。 */
    @NotNull
    private String contractTypeCode;

    /** 校区编码（可空）。 */
    private String campusCode;
}
