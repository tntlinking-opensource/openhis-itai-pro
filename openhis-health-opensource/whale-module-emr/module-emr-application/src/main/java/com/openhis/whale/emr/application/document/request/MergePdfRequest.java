/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
