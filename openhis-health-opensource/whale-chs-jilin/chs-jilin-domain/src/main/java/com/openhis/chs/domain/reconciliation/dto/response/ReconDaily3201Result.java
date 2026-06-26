/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ReconDaily3201Result {

    private String setlOptins;
    private String stmtRslt;
    private String stmtRsltDscr;
    private Long localRecordId;
}
