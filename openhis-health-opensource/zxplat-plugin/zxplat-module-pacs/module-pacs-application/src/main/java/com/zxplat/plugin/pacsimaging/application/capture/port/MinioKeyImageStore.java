/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.port;

/**
 * 关键图 MinIO 出站端口（CAP-13/14/15）；应用层禁止直接引用 MinIO SDK。
 */
public interface MinioKeyImageStore {

    /**
     * 写入关键图对象，返回对象键。
     *
     * @param workItemId 工单主键
     * @param contentType 内容类型
     * @param bytes 图像字节
     * @return 对象键
     */
    String put(Long workItemId, String contentType, byte[] bytes);

    /**
     * 按对象键删除。
     *
     * @param objectKey 对象键
     */
    void delete(String objectKey);

    /**
     * 签发预签名 GET URL。
     *
     * @param objectKey 对象键
     * @return 可打开的 URL
     */
    String presignGet(String objectKey);
}
