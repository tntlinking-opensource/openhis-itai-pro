/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicecatalog.request;

import lombok.Data;

/**
 * 下载诊疗目录导入错误标注文件请求。
 */
@Data
public class DownloadServiceCatalogImportErrorRequest {

    /** 导入失败时返回的短期 token。 */
    private String token;
}
