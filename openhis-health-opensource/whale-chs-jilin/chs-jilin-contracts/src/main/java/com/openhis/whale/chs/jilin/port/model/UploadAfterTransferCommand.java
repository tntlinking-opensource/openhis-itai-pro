/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class UploadAfterTransferCommand {

    private String transferBusNo;
    /** {@link com.openhis.health.domain.share.enums.SupplyType} 调拨单类型编码。 */
    private String supplyTypeCode;
    /** 3502 inv_chg_type：101 调拨入库 / 102 调拨出库。 */
    private String invChgType;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
