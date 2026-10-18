/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检合同单位后结收款确认请求。
 */
@Getter
@Setter
public class MarkContractPaidRequest {

    /** 合同主键。 */
    private Long contractId;
}
