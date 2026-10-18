/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.supplier.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 供应商详情查询模型。
 */
@Getter
@Setter
public class SupplierDetailQueryModel {
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
     * 统一社会信用代码（可空）。
     */
    private String creditCode;
    /**
     * 法定代表人（可空）。
     */
    private String legalRepresentative;
    /**
     * 地址（可空）。
     */
    private String address;
    /**
     * 拼音码（可空）。
     */
    private String pyStr;
    /**
     * 五笔码（可空）。
     */
    private String wbStr;
    /**
     * 联系电话（可空）。
     */
    private String phone;
    /**
     * 联系邮箱（可空）。
     */
    private String email;
    /**
     * 开户行名称（可空）。
     */
    private String bankName;
    /**
     * 银行账号（可空）。
     */
    private String bankAccount;
    /**
     * 归属机构标识（可空，按业务场景决定是否启用）。
     */
    private Long orgId;
    /**
     * 显示顺序（可空）。
     */
    private Integer displayOrder;
    /**
     * 状态枚举值（草稿/启用/停用/删除等，参见供应商状态字典）。
     */
    private String statusEnum;
    /**
     * 备注说明（可空）。
     */
    private String note;
}
