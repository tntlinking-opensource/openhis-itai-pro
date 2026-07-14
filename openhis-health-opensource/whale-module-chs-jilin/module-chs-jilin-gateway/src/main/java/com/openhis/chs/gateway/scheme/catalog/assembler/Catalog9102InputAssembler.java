/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.catalog.assembler;

import com.openhis.chs.gateway.scheme.catalog.model.Cat9102Input;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 【9102】文件下载入参组装（对账明细文件、目录增量包等 fsDownloadIn 节点） */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class Catalog9102InputAssembler {

    /**
     * 组装 9102 文件下载 input.fsDownloadIn。
     *
     * @param fileQuryNo 3202/目录下载返回的文件查询号
     * @param filename 本地保存文件名
     * @param fixmedinsCode 定点机构编号
     * @return 平台 input DTO */
    public Cat9102Input assemble(String fileQuryNo, String filename, String fixmedinsCode) {
        Cat9102Input.Cat9102FsDownloadIn fsDownloadIn = new Cat9102Input.Cat9102FsDownloadIn()
            .setFileQuryNo(fileQuryNo)
            .setFilename(filename)
            .setFixmedinsCode(fixmedinsCode);
        return new Cat9102Input().setFsDownloadIn(fsDownloadIn);
    }
}
