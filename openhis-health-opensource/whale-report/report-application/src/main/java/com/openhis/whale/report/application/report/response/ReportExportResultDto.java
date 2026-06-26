package com.openhis.whale.report.application.report.response;

import lombok.Data;

/** C 类报表 Excel 导出结果（二进制经 Base64 传输）。 */
@Data
public class ReportExportResultDto {

    /** MIME 类型，固定为 xlsx。 */
    private String contentType;
    /** 建议下载文件名。 */
    private String fileName;
    /** xlsx 文件内容的 Base64 编码。 */
    private String bodyBase64;
}
