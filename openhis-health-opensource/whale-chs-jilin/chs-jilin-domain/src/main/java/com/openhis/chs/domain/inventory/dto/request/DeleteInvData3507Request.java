/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DeleteInvData3507Request {

    private String contractBusNo;
    private Long contractId;
    /** 定点医药机构批次流水号。 */
    private String fixmedinsBchno;
    /** 1 盘存 / 2 变更 / 3 采购 / 4 销售。 */
    private String invDataType;
    /** 关联本地流水 sourceId（可选，运维补记）。 */
    private Long sourceId;
}
