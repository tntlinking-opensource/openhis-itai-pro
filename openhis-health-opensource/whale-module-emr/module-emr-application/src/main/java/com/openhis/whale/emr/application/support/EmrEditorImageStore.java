/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.support;

import com.openhis.whale.core.application.util.FileUploadUtils;
import com.openhis.whale.core.application.util.MimeTypeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;

/**
 * 编辑器图片落盘：目录 {@code {uploads}/emr/}，对外 URL 前缀 {@code /uploads/emr/}。
 */
@Component
public class EmrEditorImageStore {

    private static final String EMR_SUB_DIR = "emr";
    private static final String URL_PREFIX = "/uploads/emr/";

    /**
     * 解码 Base64/data URL 并写入上传目录。
     *
     * @param contentBase64 data URL 或纯 Base64
     * @param fileName 原始文件名，用于扩展名推断
     * @param mimeType 可选 MIME
     * @return 浏览器可请求的相对 URL
     */
    public String saveImage(String contentBase64, String fileName, String mimeType) throws IOException {
        if (!StringUtils.hasText(contentBase64)) {
            throw new IOException("图片内容为空");
        }
        String payload = contentBase64.trim();
        String resolvedMime = mimeType;
        if (payload.startsWith("data:")) {
            int comma = payload.indexOf(',');
            if (comma < 0) {
                throw new IOException("data URL 格式无效");
            }
            String header = payload.substring(0, comma);
            if (!StringUtils.hasText(resolvedMime) && header.contains(";")) {
                resolvedMime = header.substring(5, header.indexOf(';'));
            }
            payload = payload.substring(comma + 1);
        }
        byte[] bytes = Base64.getDecoder().decode(payload);
        if (bytes.length == 0) {
            throw new IOException("图片解码后为空");
        }
        if (bytes.length > FileUploadUtils.DEFAULT_MAX_SIZE) {
            throw new IOException("图片超过大小限制");
        }
        String ext = resolveImageExtension(fileName, resolvedMime);
        if (!FileUploadUtils.isAllowedExtension(ext, MimeTypeUtils.IMAGE_EXTENSION)) {
            throw new IOException("不允许的图片类型：" + ext);
        }
        String uploadName;
        if (StringUtils.hasText(fileName)) {
            uploadName = fileName;
        } else {
            uploadName = "image." + ext;
        }
        String relative = FileUploadUtils.generateFileName(uploadName);
        String baseDir = FileUploadUtils.getDefaultBaseDir() + "/" + EMR_SUB_DIR;
        var dest = FileUploadUtils.getAbsoluteFile(baseDir, relative);
        Files.write(dest.toPath(), bytes);
        return URL_PREFIX + relative.replace('\\', '/');
    }

    private static String resolveImageExtension(String fileName, String mimeType) {
        if (StringUtils.hasText(fileName)) {
            String fromName = FileUploadUtils.getExtension(fileName);
            if (StringUtils.hasText(fromName)) {
                return fromName.toLowerCase(Locale.ROOT);
            }
        }
        if (StringUtils.hasText(mimeType)) {
            String fromMime = MimeTypeUtils.getExtension(mimeType);
            if (StringUtils.hasText(fromMime)) {
                return fromMime.toLowerCase(Locale.ROOT);
            }
        }
        return "png";
    }
}
