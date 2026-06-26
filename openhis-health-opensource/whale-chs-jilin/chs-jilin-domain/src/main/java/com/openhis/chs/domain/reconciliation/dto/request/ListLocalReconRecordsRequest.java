/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ListLocalReconRecordsRequest {

    private String contractBusNo;
    /** yyyyMM，可空 */
    private String monthFilter;
}
