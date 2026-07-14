/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.reconciliation.request;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地对账/清算流水查询入参。 */
@Data
@Accessors(chain = true)
public class ListLocalReconRecordsRequest {

    /** 契约业务编号 */
    private String contractBusNo;
    /** 月份筛选，格式 yyyyMM */
    private String monthFilter;
}
