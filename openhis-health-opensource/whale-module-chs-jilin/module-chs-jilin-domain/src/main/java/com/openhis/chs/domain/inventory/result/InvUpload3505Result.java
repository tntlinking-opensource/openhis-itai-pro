/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3505】商品销售上传出参 */
@Data
@Accessors(chain = true)
public class InvUpload3505Result {

    private Long sourceLineId;
    private Long uploadId;
    private String uploadStatus;
    private String platformRetRslt;
    private String platformMsg;
    private String fixmedinsBchno;
}
