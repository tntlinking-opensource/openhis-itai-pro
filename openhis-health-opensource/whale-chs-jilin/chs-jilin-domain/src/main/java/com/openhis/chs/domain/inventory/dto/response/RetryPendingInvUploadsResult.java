/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RetryPendingInvUploadsResult {

    private int attempted;

    private int succeeded;

    private int failed;

    private int skipped;
}
