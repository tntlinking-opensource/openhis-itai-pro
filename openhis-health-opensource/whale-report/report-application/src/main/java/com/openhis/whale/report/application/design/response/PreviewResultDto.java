package com.openhis.whale.report.application.design.response;
import com.openhis.whale.report.application.template.response.PreviewLayoutMetaDto;

import lombok.Getter;

/**
 * 排版预览结果。PDF 以 Base64 下发；overlay 叠图元数据为结构化字段，禁止下发 layoutJson。
 */
@Getter
public class PreviewResultDto {

    private final String contentType;
    private final String fileName;
    private final String bodyBase64;
    private final String contentHash;
    private final PreviewLayoutMetaDto previewMeta;

    public PreviewResultDto(
            String contentType,
            String fileName,
            String bodyBase64,
            String contentHash) {
        this(contentType, fileName, bodyBase64, contentHash, null);
    }

    public PreviewResultDto(
            String contentType,
            String fileName,
            String bodyBase64,
            String contentHash,
            PreviewLayoutMetaDto previewMeta) {
        this.contentType = contentType;
        this.fileName = fileName;
        this.bodyBase64 = bodyBase64;
        this.contentHash = contentHash;
        this.previewMeta = previewMeta;
    }
}
