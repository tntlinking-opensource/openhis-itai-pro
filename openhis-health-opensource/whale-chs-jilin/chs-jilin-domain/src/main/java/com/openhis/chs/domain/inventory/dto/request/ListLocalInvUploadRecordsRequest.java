/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ListLocalInvUploadRecordsRequest {

    private String sourceType;
    private Long sourceId;
    private String infno;
}
