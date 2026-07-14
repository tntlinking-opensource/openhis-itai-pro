/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.support.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 物流调价在途预检结果（IF-PRC-08 / BR-PRC-SUB-003）。
 */
@Getter
@Setter
public class PriceAdjustmentInTransitCheckDto {

    /** 是否阻断提交。 */
    private boolean blocked;

    /** 原因码（{@link com.openhis.health.domain.share.constants.CustomConstants.PriceAdjustment}）。 */
    private String reasonCode;

    /** 用户可读说明。 */
    private String reasonMessage;

    /** 阻断项目表名。 */
    private String itemTable;

    /** 阻断项目主键。 */
    private Long itemId;
}
