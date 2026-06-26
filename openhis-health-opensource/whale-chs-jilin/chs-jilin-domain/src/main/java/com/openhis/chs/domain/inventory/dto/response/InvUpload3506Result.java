/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class InvUpload3506Result {

    private Long sourceLineId;
    private Long uploadId;
    private String uploadStatus;
    private String platformRetRslt;
    private String platformMsg;
    private String fixmedinsBchno;
}
