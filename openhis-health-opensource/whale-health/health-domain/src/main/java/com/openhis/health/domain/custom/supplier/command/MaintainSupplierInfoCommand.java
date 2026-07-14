/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.supplier.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 维护供应商基础信息命令。
 */
@Getter
@Setter
public class MaintainSupplierInfoCommand {
    /**
     * 供应商标识。
     */
    private Long supplierId;
    /**
     * 供应商名称（可空；非空则覆盖）。
     */
    private String name;
    /**
     * 供应商类型枚举值（可空；非空则覆盖）。
     */
    private String typeEnum;
    /**
     * 法定代表人（可空；非空则覆盖）。
     */
    private String legalRepresentative;
    /**
     * 地址（可空；非空则覆盖）。
     */
    private String address;
    /**
     * 拼音码（可空；非空则覆盖）。
     */
    private String pyStr;
    /**
     * 五笔码（可空；非空则覆盖）。
     */
    private String wbStr;
    /**
     * 联系电话（可空；非空则覆盖）。
     */
    private String phone;
    /**
     * 联系邮箱（可空；非空则覆盖）。
     */
    private String email;
    /**
     * 开户行名称（可空；非空则覆盖）。
     */
    private String bankName;
    /**
     * 银行账号（可空；非空则覆盖）。
     */
    private String bankAccount;
    /**
     * 显示顺序（可空；非空则覆盖）。
     */
    private Integer displayOrder;
    /**
     * 备注说明（可空；非空则覆盖）。
     */
    private String note;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
