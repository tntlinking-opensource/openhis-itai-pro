/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class UploadAfterStocktakeCommand {

    private String stocktakeBusNo;
    /** {@link com.openhis.health.domain.share.enums.SupplyType} 盘点类型编码。 */
    private String supplyTypeCode;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
    /** 可选覆盖 {@link com.openhis.chs.domain.inventory.ChsInventoryConstants#SOURCE_TYPE_STOCKTAKE}。 */
    private String sourceType;
}
