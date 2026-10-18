/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3501】商品盘存上传出参 */
@Data
@Accessors(chain = true)
public class InvUpload3501Result {

    private Long uploadId;
    private Long sourceLineId;
    private String fixmedinsBchno;
    private String uploadStatus;
    private String platformMsg;
}
