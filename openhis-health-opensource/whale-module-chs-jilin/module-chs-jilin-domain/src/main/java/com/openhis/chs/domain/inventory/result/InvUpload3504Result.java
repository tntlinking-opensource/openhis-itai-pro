/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3504】商品采购退货上传出参 */
@Data
@Accessors(chain = true)
public class InvUpload3504Result {

    private Long uploadId;
    private Long sourceLineId;
    private String fixmedinsBchno;
    private String uploadStatus;
    private String platformMsg;
}
