/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RetryPendingInvUploadsRequest {

    /** 单次处理上限；空则用配置默认值。 */
    private Integer batchSize;
}
