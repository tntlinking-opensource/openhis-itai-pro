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
public class UploadInvChange3502Request {

    private String contractBusNo;
    private Long contractId;
    private String remark;
    private Long sourceId;
    /** {@link com.openhis.chs.domain.inventory.ChsInventoryConstants} SOURCE_TYPE_* */
    private String sourceType;
    private List<InvUploadLineCommand> lines = new ArrayList<>();
}
