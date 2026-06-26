/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InitializeInventoryLineResult {

    private Long inventoryItemId;
    private String fixmedinsBchno;
    private String medListCodg;
    private String upload3501Status;
    private String upload3502Status;
    private Long upload3501Id;
    private Long upload3502Id;
    private String platformMsg;
}
