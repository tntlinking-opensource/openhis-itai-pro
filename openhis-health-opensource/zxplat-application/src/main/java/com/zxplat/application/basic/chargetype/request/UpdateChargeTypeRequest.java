/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargetype.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑费用性质请求体：在 {@link CreateChargeTypeRequest} 字段基础上携带技术主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateChargeTypeRequest extends CreateChargeTypeRequest {

    /** 费用性质（合约）主键。 */
    private Long id;
}
