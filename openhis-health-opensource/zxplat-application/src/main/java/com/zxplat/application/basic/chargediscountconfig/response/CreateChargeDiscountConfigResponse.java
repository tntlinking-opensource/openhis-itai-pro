/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargediscountconfig.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

/**
 * 新增收费折扣配置响应（IF-CDC-03）：仅回传新行主键。
 */
@Getter
@Setter
public class CreateChargeDiscountConfigResponse {

    /**
     * 新建配置主键（JSON 字符串）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
}
