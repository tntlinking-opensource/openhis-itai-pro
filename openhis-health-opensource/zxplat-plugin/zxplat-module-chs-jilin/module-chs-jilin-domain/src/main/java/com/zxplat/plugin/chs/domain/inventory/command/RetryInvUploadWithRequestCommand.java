/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 单条进销存补传：可选覆盖 request_json 后重放平台报文 */
@Data
@Accessors(chain = true)
public class RetryInvUploadWithRequestCommand {

    /** 本地上报流水主键 */
    private Long uploadId;

    /** 覆盖后的请求 JSON；空则沿用库内 request_json */
    private String requestJson;
}
