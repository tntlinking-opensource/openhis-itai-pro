/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/** 13xx 文件型目录下载 output 壳（1301/1302/1305 等 返回文件元数据） */
@Data
public class CatFileDownloadOutput {

    @JSONField(name = "file_qury_no")
    private String fileQuryNo;

    @JSONField(name = "filename")
    private String filename;

    @JSONField(name = "data_cnt")
    private Long dataCnt;

    @JSONField(name = "dld_end_time")
    private String dldEndTime;
}
