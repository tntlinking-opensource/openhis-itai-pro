/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3507】商品信息删除出参 */
@Data
@Accessors(chain = true)
public class InvDelete3507Result {

    private Long uploadId;
    private String fixmedinsBchno;
    private String invDataType;
    private String uploadStatus;
    private String platformMsg;
}
