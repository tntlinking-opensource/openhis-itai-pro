/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class InventoryUploadAfterDispenseCommand {

    private Long encounterId;
    private List<Long> dispenseIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
