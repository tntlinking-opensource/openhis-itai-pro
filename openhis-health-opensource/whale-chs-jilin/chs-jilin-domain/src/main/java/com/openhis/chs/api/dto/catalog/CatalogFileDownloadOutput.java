package com.openhis.chs.api.dto.catalog;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 13xx 文件型目录下载平台 output（1301/1302/1305… 字段相同）。
 */
@Data
public class CatalogFileDownloadOutput {

    @JSONField(name = "file_qury_no")
    private String fileQuryNo;

    @JSONField(name = "filename")
    private String filename;

    @JSONField(name = "data_cnt")
    private Long dataCnt;

    @JSONField(name = "dld_end_time")
    private String dldEndTime;
}
