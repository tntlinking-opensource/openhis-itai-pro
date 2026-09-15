/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建体检号源预约请求。
 *
 * <p>contractTypeCode 由前端从 peis listValidContractsForPatient 取得后传入；booking 不直连 peis。</p>
 */
@Getter
@Setter
public class CreateHcSlotBookingRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 体检套餐主键。 */
    @NotNull
    private Long packageId;

    /** 占用的时段配额主键。 */
    @NotNull
    private Long daySlotId;

    /** 团检合同主键（散客可空）。 */
    private Long contractId;

    /** 进池合同类型编码（有合同时必填；无合同时服务端强制 WALK_IN）。 */
    private String contractTypeCode;

    /** 预约来源渠道。 */
    private String originChannel;
}
