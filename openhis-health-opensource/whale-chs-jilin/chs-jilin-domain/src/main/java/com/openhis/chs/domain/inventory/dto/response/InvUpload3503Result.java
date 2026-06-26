/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InvUpload3503Result {

    private Long uploadId;
    private Long sourceLineId;
    private String fixmedinsBchno;
    private String uploadStatus;
    private String platformMsg;
}
