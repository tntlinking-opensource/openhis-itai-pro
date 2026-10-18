/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.client;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【9101】文件上传出参 */
@Data
@Accessors(chain = true)
public class ChsFileUploadResult {

    private String fileQuryNo;
    private String filename;
    private String fixmedinsCode;
    private String dldEndtime;
}
