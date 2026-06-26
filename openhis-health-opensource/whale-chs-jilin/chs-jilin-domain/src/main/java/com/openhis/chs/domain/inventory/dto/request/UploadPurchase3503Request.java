/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.request;

import com.openhis.chs.domain.inventory.dto.InvUploadLineCommand;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class UploadPurchase3503Request {

    private String contractBusNo;
    private Long contractId;
    private String inboundBusNo;
    private Long sourceId;
    private List<InvUploadLineCommand> lines = new ArrayList<>();
}
