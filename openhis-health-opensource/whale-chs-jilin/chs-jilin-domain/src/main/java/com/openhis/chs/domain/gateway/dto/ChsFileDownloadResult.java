package com.openhis.chs.domain.gateway.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** AppService HTTP 传输 ZIP 字节（Jackson 以 Base64 序列化）。 */
@Data
@Accessors(chain = true)
public class ChsFileDownloadResult {

    private byte[] content;
}
