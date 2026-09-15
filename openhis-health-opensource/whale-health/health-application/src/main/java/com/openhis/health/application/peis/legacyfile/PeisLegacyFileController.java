/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.legacyfile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 迁院后存量 PEIS 报告/照片只读接口（需登录；配置键仍为 {@code openhis.peis.legacy-file}）。
 *
 * <p>例：{@code GET /api/app/peis/legacy-file/report/张三-….pdf}</p>
 * <p>或：{@code GET /api/app/peis/legacy-file?path=/checkOrder/202498/a.jpg}</p>
 */
@RestController
@RequestMapping("/api/app/peis/legacy-file")
@ConditionalOnProperty(prefix = "openhis.peis.legacy-file", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PeisLegacyFileController {

    private final PeisLegacyFileAccessService accessService;

    /**
     * 按 URL 尾部相对路径读取（路径形态对齐迁院前 {@code /platform/file/v1/images/**}）。
     */
    @GetMapping("/**")
    public ResponseEntity<Resource> getByPath(HttpServletRequest request,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download)
            throws IOException {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String relative = new AntPathMatcher().extractPathWithinPattern(pattern, fullPath);
        return buildResponse(relative, download);
    }

    /**
     * 按 query 参数读取（便于带特殊字符的 PDF 名）。
     */
    @GetMapping
    public ResponseEntity<Resource> getByQuery(
            @RequestParam("path") String path,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download)
            throws IOException {
        return buildResponse(path, download);
    }

    private ResponseEntity<Resource> buildResponse(String raw, boolean download) throws IOException {
        Path file = accessService.resolveReadableFile(raw);
        Resource body = new FileSystemResource(file);
        String fileName = file.getFileName().toString();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (download ? "attachment" : "inline") + "; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(accessService.contentTypeOf(file)))
                .contentLength(Files.size(file))
                .body(body);
    }
}
