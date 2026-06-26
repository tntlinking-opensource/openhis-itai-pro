package com.openhis.chs.api.dto.catalog;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

/**
 * 13xx 文件型目录下载入参（{@code data} 节点，1301/1302/1305… 结构相同）。
 */
@Data
@Builder
public class CatalogFileDownloadInput {

    /** 本地最大版本号 */
    @JSONField(name = "ver")
    private String ver;
}
