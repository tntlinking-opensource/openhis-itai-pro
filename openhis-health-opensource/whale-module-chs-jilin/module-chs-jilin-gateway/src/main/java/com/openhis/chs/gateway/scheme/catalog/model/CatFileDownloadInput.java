/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.model;

import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 13xx 文件型目录下载 input 壳 */
public class CatFileDownloadInput extends ChsDataInput<CatFileDownloadInput.CatFileDownloadData> {

    /** 13xx input.data 节点 */
    @Data
    @Accessors(chain = true)
    public static class CatFileDownloadData {

        private String ver;
    }
}
