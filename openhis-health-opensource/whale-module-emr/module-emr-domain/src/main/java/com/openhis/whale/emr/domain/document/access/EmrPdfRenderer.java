/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.access;

/**
 * EMR 文书 JSON → PDF 渲染端口（实现由 application 装配，bridge 仅依赖本接口）。
 *
 * <p>副作用：纯内存渲染，不写库；未装配时 bridge {@link com.openhis.health.integration.emr.mrms.EmrDocumentExtensionImpl}
 * 导出 PDF 将抛业务异常。</p>
 */
public interface EmrPdfRenderer {

    /**
     * 将文书结构化 JSON 渲染为 PDF 字节流。
     *
     * @param contentJson 文书 content JSON
     * @param command 文档主键、预览模式等渲染参数
     * @return PDF 字节与元信息
     */
    EmrPdfRenderResult render(String contentJson, EmrPdfRenderCommand command);
}
