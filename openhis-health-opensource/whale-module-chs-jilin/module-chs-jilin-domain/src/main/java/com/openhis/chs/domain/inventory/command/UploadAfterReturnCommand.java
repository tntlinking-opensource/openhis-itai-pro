/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 退药完成后触发 3506→3502(106) 销售退货上报（伴生批次=退药单号） */
@Data
@Accessors(chain = true)
public class UploadAfterReturnCommand {

    private Long encounterId;
    private List<Long> dispenseIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
    /**
     * 操作人主键：异步组装 3506 时查 3511 须带入（{@code @Async} 无登录态）。
     */
    private Long operatorId;
    /** 操作人姓名；可空。 */
    private String operatorName;
}
