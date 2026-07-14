/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地上报流水查询单行 */
@Data
@Accessors(chain = true)
public class LocalInvUploadResult {

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
