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

package com.openhis.whale.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PdfGenerateResultDto {

    /** 资源访问 URL（相对路径） */
    private String url;
    /** token（业务字段） */
    private String token;
    /** Base64 编码的 PDF 字节流，供前端预览/下载 */
    private String pdfBase64;
    /** PDF 页数 */
    private Integer pageCount;
    /** 内容与打印配置 SHA-256，用于缓存校验 */
    private String contentHash;
    /** renderedAt（业务字段） */
    private Instant renderedAt;
    /** previewTruncated（业务字段） */
    private Boolean previewTruncated;
}
