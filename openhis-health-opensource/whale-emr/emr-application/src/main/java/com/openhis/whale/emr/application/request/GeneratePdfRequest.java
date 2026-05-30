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

package com.openhis.whale.emr.application.request;

import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class GeneratePdfRequest {

    /** 雪花 ID 建议字符串传参，避免 JSON 数字精度丢失 */
    private String documentId;
    /** 可选：前端直接传 content JSON 字符串 */
    private String content;
    private String pageRange;
    private List<String> regionNames;
    private Boolean includeTrackChanges;
    private String watermark;
    private String headerTemplate;
    private String footerTemplate;
    /** true=预览（默认）；false=正式打印 */
    private Boolean previewMode = Boolean.TRUE;
    private PrintConfig printConfig;
    private Integer previewMaxPages;
    private String contentHash;
    private Object patientId;
    private Object encounterId;
    private String documentTitle;
    private String documentTypeName;
    private Map<String, String> metadata;
}
