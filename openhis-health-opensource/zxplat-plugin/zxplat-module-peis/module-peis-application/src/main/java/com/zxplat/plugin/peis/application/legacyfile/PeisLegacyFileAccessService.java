/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.legacyfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;

import lombok.RequiredArgsConstructor;

/**
 * 老 PEIS 文件只读解析：storage key → 真源根下的安全绝对路径。
 */
@Service
@RequiredArgsConstructor
public class PeisLegacyFileAccessService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "pdf", "bmp", "webp");

    private final PeisLegacyFileProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getRoot());
    }

    /**
     * 将任意库内路径或相对 key 解析为可读文件。
     *
     * @param rawPathOrKey 相对 key 或含旧绝对前缀的路径
     * @return 绝对路径；未启用或非法时抛业务异常
     */
    public Path resolveReadableFile(String rawPathOrKey) {
        if (!isEnabled()) {
            throw new BusinessException("PEIS_LEGACY_FILE_DISABLED", "未启用老 PEIS 文件读取或未配置 root");
        }
        if (!StringUtils.hasText(rawPathOrKey)) {
            throw new ValidationException("文件路径不能为空");
        }
        String key = PeisLegacyFilePathNormalizer.normalize(rawPathOrKey, stripPrefixes());
        if (!StringUtils.hasText(key) || key.contains("..")) {
            throw new ValidationException("非法文件路径");
        }
        String ext = extensionOf(key);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ValidationException("不允许的文件类型：" + ext);
        }

        Path root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new ValidationException("禁止目录穿越");
        }
        if (!Files.isRegularFile(file)) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在：" + key);
        }
        return file;
    }

    /**
     * 探测相对 key 在真源下是否存在（验收用）。
     */
    public Optional<Path> findIfExists(String rawPathOrKey) {
        try {
            return Optional.of(resolveReadableFile(rawPathOrKey));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public String contentTypeOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        try {
            String probed = Files.probeContentType(file);
            if (probed != null) {
                return probed;
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "image/jpeg";
    }

    private List<String> stripPrefixes() {
        List<String> list = new ArrayList<>();
        if (StringUtils.hasText(properties.getRoot())) {
            list.add(properties.getRoot());
        }
        if (properties.getAliasRoots() != null) {
            list.addAll(properties.getAliasRoots());
        }
        return list;
    }

    private static String extensionOf(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0 || dot == key.length() - 1) {
            return "";
        }
        return key.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
