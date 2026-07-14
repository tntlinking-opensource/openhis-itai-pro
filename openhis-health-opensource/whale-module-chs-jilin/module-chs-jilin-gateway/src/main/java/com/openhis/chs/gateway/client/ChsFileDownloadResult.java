/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.client;

import lombok.Data;
import lombok.experimental.Accessors;

/** 文件下载 HTTP 传输结果（Jackson 以 Base64 序列化字节）。 */
@Data
@Accessors(chain = true)
public class ChsFileDownloadResult {

    private byte[] content;
}
