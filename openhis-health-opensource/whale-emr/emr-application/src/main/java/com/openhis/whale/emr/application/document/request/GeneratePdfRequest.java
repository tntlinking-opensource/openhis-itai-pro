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
    /** 打印页范围表达式 */
    private String pageRange;
    /** 限定打印的区域节点 name 列表 */
    private List<String> regionNames;
    /** 是否渲染修订痕迹 */
    private Boolean includeTrackChanges;
    /** 水印配置 */
    private String watermark;
    /** 页眉模板字符串 */
    private String headerTemplate;
    /** 页脚模板字符串 */
    private String footerTemplate;
    /** true=预览（默认）；false=正式打印 */
    private Boolean previewMode = Boolean.TRUE;
    /** 打印版式配置对象 */
    private PrintConfig printConfig;
    /** 预览最大页数 */
    private Integer previewMaxPages;
    /** 内容与打印配置 SHA-256，用于缓存校验 */
    private String contentHash;
    /** 患者 id（字符串） */
    private Object patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private Object encounterId;
    /** PDF 页眉用文书标题 */
    private String documentTitle;
    /** PDF 页眉用文书类型名 */
    private String documentTypeName;
    /** 打印/渲染用元数据键值 */
    private Map<String, String> metadata;
}
