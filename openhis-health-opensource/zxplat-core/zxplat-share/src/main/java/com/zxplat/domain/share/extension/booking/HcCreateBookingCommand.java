/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.booking;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检号源下单命令（跨模块 SPI）。
 */
@Getter
@Setter
public class HcCreateBookingCommand {

    /** 机构主键。 */
    private Long organizationId;

    /** 患者主键。 */
    private Long patientId;

    /** 套餐主键。 */
    private Long packageId;

    /** 时段配额主键。 */
    private Long daySlotId;

    /** 合同主键（散客可空）。 */
    private Long contractId;

    /** 合同类型编码。 */
    private String contractTypeCode;

    /** 来源渠道（如 MINIAPP）。 */
    private String originChannel;

    /** 操作人。 */
    private Long operatorId;
}
