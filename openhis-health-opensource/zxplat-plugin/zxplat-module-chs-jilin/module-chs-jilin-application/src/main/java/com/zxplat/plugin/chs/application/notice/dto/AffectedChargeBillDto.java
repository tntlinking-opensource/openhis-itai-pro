/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.dto;

import lombok.Data;

/**
 * 受影响未收费单据 DTO（apply_log 侧痕迹，供后续查询扩展）。
 */
@Data
public class AffectedChargeBillDto {

    /** 单据主键。 */
    private Long billId;

    /** 单据号。 */
    private String billNo;

    /** 就诊主键。 */
    private Long encounterId;

    /** 明细行主键。 */
    private Long itemLineId;

    /** 改前等级。 */
    private String beforeChrgitmLv;

    /** 改后等级。 */
    private String afterChrgitmLv;
}
