/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 【4102】结算清单状态修改请求。
 */
@Getter
@Setter
public class ModifySettleListStatusRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 费用性质合同业务号。 */
    private String contractBusNo;

    /** 审核状态：0 撤回为待提交 / 1 提交审核。 */
    private String stasType;
}
