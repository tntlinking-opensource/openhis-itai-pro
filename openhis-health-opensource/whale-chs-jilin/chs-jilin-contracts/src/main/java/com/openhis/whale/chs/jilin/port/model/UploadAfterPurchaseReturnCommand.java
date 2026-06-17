/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 药库向供应商退货同意后触发 3504。 */
@Data
@Accessors(chain = true)
public class UploadAfterPurchaseReturnCommand {

    private String returnBusNo;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
