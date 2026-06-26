/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.dto.catalog.Catalog9102DownloadInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 【9102】文件下载入参组装（对账明细文件、目录增量包等 fsDownloadIn 节点）。 */
@Component
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class Catalog9102InputAssembler {

    /**
     * 组装 9102 文件下载 input.fsDownloadIn。
     *
     * @param fileQuryNo 3202/目录下载返回的文件查询号
     * @param filename 本地保存文件名
     * @param fixmedinsCode 定点机构编号
     * @return 平台 input JSON
     */
    public JSONObject assemble(String fileQuryNo, String filename, String fixmedinsCode) {
        Catalog9102DownloadInput fsDownloadIn = Catalog9102DownloadInput.builder()
            .fileQuryNo(fileQuryNo)
            .filename(filename)
            .fixmedinsCode(fixmedinsCode)
            .build();
        JSONObject root = new JSONObject();
        root.put("fsDownloadIn", JSON.toJSON(fsDownloadIn));
        return root;
    }
}
