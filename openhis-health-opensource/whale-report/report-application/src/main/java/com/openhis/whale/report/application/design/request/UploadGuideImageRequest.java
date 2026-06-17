package com.openhis.whale.report.application.design.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 套打定位纸上传：前端传 data URL 或纯 Base64，服务端落盘并返回可访问相对 URL。
 */
@Getter
@Setter
public class UploadGuideImageRequest {

    /** data:image/png;base64,... 或纯 Base64 载荷 */
    @NotBlank
    private String contentBase64;

    /** 原始文件名（用于推断扩展名），可选 */
    private String fileName;

    /** MIME，如 image/png；data URL 未带时可补充 */
    private String mimeType;
}
