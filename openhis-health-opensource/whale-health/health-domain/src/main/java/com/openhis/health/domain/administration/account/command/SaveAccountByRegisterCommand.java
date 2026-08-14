/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.account.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 挂号场景保存账户命令：登记就诊时同步开立或定位账户并标记默认属性。
 */
@Getter
@Setter
public class SaveAccountByRegisterCommand {
    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 账户类型编码。 */
    private String typeCode;
    /** 是否默认账户。 */
    private Boolean defaultFlag;
    /** 费用性质业务编码（{@code fin_contract.bus_no}，写入 {@code adm_account.contract_no}）。 */
    private String contractNo;
    /**
     * 险种类型码（国家医保：310 职工 / 390 城乡居民等）。
     * 仅医保账户写入；个人现金户可空。
     */
    private String insutype;
    /** 操作人主键。 */
    private Long operatorId;
}
