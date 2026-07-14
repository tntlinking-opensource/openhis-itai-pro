/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.supplier.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 注册供应商命令。
 */
@Getter
@Setter
public class RegisterSupplierCommand {
    /**
     * 供应商业务编码（可空；若由系统生成则忽略）。
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
     * 归属机构标识（可空）。
     */
    private Long orgId;
    /**
     * 显示顺序（可空）。
     */
    private Integer displayOrder;
    /**
     * 备注说明（可空）。
     */
    private String note;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
