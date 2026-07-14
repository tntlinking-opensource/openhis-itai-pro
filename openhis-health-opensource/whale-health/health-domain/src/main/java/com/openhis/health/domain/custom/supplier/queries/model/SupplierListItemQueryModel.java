/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.supplier.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 供应商列表项查询模型。
 */
@Getter
@Setter
public class SupplierListItemQueryModel {
    /**
     * 供应商标识。
     */
    private Long id;
    /**
     * 供应商业务编码。
     */
    private String busNo;
    /**
     * 供应商名称。
     */
    private String name;
    /**
     * 供应商类型枚举值。
     */
    private String typeEnum;
    /**
     * 状态枚举值。
     */
    private String statusEnum;
    /**
     * 拼音码（可空）。
     */
    private String pyStr;
    /**
     * 地址（可空）。
     */
    private String address;
    /**
     * 联系电话（可空）。
     */
    private String phone;
    /**
     * 电子邮箱（可空）。
     */
    private String email;
}
