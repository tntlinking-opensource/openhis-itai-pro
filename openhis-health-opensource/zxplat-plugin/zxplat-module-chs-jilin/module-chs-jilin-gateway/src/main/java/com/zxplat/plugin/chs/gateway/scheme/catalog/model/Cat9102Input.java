/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【㤱〲】文件下载 input 壳 */
@Data
@Accessors(chain = true)
public class Cat9102Input {

    @JSONField(name = "fsDownloadIn")
    private Cat9102FsDownloadIn fsDownloadIn;

    /** 9102 input.fsDownloadIn 节点 */
    @Data
    @Accessors(chain = true)
    public static class Cat9102FsDownloadIn {

        @JSONField(name = "file_qury_no")
        private String fileQuryNo;

        private String filename;

        @JSONField(name = "fixmedins_code")
        private String fixmedinsCode;
    }
}
