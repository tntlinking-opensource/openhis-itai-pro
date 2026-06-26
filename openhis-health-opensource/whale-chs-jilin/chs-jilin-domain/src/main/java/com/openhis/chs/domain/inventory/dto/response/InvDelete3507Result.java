/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InvDelete3507Result {

    private Long uploadId;
    private String fixmedinsBchno;
    private String invDataType;
    private String uploadStatus;
    private String platformMsg;
}
