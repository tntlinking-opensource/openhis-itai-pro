package com.openhis.whale.report.application.report.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 保存报表导出元数据（PDF 开关与模板编码）请求。 */
@Data
public class SaveReportDefinitionRequest {

    /** 报表定义主键。 */
    @NotNull
    private Long id;

    /** 展示名称（可选更新）。 */
    private String name;

    /** 是否启用 PDF 导出。 */
    private Boolean exportPdfEnabled;

    /** PDF 打印模板编码；exportPdfEnabled 为 true 时发布前必填。 */
    @JsonAlias("pdfPrintCode")
    private String pdfTemplateCode;
}
