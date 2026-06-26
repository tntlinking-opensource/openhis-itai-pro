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
public class UploadAfterReturnCommand {

    private Long encounterId;
    private List<Long> dispenseIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
