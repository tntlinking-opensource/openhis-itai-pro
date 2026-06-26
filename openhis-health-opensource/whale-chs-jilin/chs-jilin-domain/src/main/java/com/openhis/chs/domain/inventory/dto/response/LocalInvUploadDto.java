/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LocalInvUploadDto {

    private Long id;
    private String contractBusNo;
    private String infno;
    private String fixmedinsBchno;
    private String medListCodg;
    private String sourceType;
    private Long sourceId;
    private Long sourceLineId;
    private String uploadStatus;
    private String platformRetRslt;
    private String platformMsg;
    private String createdAt;
}
