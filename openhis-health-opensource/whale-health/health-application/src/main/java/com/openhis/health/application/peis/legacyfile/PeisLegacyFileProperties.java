/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.legacyfile;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 迁院后存量 PEIS 报告/照片只读根目录配置（NENU-MIG-FILE-001）。
 *
 * <pre>
 * openhis:
 *   peis:
 *     legacy-file:
 *       enabled: true
 *       root: D:/0.product/ftpuser/images
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "openhis.peis.legacy-file")
public class PeisLegacyFileProperties {

    /** 是否启用存量 PEIS 文件只读。 */
    private boolean enabled = false;

    /**
     * 真源根目录（绝对路径）。例：{@code D:/0.product/ftpuser/images}
     */
    private String root = "";

    /**
     * 路径别名根（仅用于归一时剥离 URL 前缀，不作为读盘真源）。
     */
    private List<String> aliasRoots = new ArrayList<>();
}
