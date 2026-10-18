/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑器图片上传：前端传 data URL 或纯 Base64，服务端落盘并返回可访问相对 URL。
 */
@Getter
@Setter
public class UploadEditorImageRequest {

    /** data:image/png;base64,... 或纯 Base64 载荷 */
    @NotBlank
    /** 图片 Base64 或 data URL */
    private String contentBase64;

    /** 原始文件名（用于推断扩展名），可选 */
    private String fileName;

    /** MIME，如 image/png；data URL 未带时可补充 */
    private String mimeType;
}
