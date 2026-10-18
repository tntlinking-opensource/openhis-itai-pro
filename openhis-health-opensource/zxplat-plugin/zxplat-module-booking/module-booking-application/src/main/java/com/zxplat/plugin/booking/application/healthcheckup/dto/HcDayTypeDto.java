/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 日开放合同类型 DTO。
 */
@Getter
@Setter
public class HcDayTypeDto {

    /** 主键。 */
    private Long id;

    /** 合同类型编码。 */
    private String contractTypeCode;
}
