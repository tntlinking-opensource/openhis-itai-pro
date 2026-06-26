package com.openhis.chs.api.dto.catalog;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

/**
 * 【9102】文件下载 fsDownloadIn 节点。
 */
@Data
@Builder
public class Catalog9102DownloadInput {

    @JSONField(name = "file_qury_no")
    private String fileQuryNo;

    @JSONField(name = "filename")
    private String filename;

    @JSONField(name = "fixmedins_code")
    private String fixmedinsCode;
}
