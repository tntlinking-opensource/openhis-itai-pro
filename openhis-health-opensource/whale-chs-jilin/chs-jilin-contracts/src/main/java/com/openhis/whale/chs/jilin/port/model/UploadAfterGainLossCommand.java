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
public class UploadAfterGainLossCommand {

    private String gainLossBusNo;
    /** {@link com.openhis.health.domain.share.enums.SupplyType} 损益单类型编码。 */
    private String supplyTypeCode;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
