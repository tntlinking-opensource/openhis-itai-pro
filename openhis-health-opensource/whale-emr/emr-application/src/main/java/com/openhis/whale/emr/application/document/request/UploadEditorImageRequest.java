/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.document.request;

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
