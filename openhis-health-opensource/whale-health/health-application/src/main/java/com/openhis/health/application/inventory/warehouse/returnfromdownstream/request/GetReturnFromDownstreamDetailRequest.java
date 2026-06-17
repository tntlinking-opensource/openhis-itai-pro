/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.request;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class GetReturnFromDownstreamDetailRequest {

    /** 业务单号。 */
    @Schema(description = "业务单号 busNo")
    private String busNo;
}
