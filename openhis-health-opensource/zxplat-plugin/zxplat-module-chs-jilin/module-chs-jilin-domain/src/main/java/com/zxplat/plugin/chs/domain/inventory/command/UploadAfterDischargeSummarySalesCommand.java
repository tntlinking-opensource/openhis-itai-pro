/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 清床后触发汇总径净销售 3505 上报命令。
 *
 * <p>仅携带就诊与契约；明细由 bridge Loader 按 {@code summary_no} 发退相抵并按天+单价分桶后组装；
 * 本地来源 {@code CLEAR_BED_SUMMARY}/{@code encounterId}。</p>
 */
@Data
@Accessors(chain = true)
public class UploadAfterDischargeSummarySalesCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 契约业务号；可空。 */
    private String contractBusNo;

    /** 契约主键；可空。 */
    private Long contractId;
}
