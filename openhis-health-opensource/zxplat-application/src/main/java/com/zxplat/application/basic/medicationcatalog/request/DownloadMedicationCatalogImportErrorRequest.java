/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.request;

import lombok.Data;

/**
 * 下载药品目录导入错误标注文件请求。
 */
@Data
public class DownloadMedicationCatalogImportErrorRequest {

    /** IMP-02 失败时返回的短期 token。 */
    private String token;
}
