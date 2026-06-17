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

@Getter
@Setter
public class MergePdfRequest {

    /** 待合并文书 ID 列表（雪花 ID 建议字符串） */
    private List<String> documentIds;
    /** 预览模式（限制最大页数） */
    private Boolean previewMode = Boolean.TRUE;
    /** 打印版式配置对象 */
    private PrintConfig printConfig;
    /** 患者 id（字符串） */
    private Object patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private Object encounterId;
    /** 打印/渲染用元数据键值 */
    private java.util.Map<String, String> metadata;
}
