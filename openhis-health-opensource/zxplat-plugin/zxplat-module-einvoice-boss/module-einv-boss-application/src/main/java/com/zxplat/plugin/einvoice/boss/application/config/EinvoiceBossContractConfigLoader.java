/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 从 {@code einvoice-boss-contract.properties} 按医疗机构编码加载博思平台配置。
 */
public final class EinvoiceBossContractConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(EinvoiceBossContractConfigLoader.class);

    private static final String CONFIG_FILE = "einvoice-boss-contract.properties";
    private static final String CONFIG_PREFIX = "contract.";
    private static final String CONFIG_PATH_PROP = "einvoice.boss.contract.config.path";
    private static final String CONFIG_PATH_ENV = "EINVOICE_BOSS_CONTRACT_CONFIG_PATH";

    private static final Lock LOCK = new ReentrantLock();

    private static volatile Map<String, EinvoiceBossContractConfig> cache;
    private static volatile long lastLoadedMtime;

    private EinvoiceBossContractConfigLoader() {
    }

    public static EinvoiceBossContractConfig getByOrgCode(String orgCode) {
        if (orgCode == null || orgCode.isBlank()) {
            return null;
        }
        reloadIfStale();
        return cache.get(orgCode.trim());
    }

    /** 返回首个已启用的契约配置（单院 embedded 默认场景）。 */
    public static EinvoiceBossContractConfig getDefault() {
        reloadIfStale();
        for (EinvoiceBossContractConfig config : cache.values()) {
            if (config.isEnabled()) {
                return config;
            }
        }
        return null;
    }

    private static void reloadIfStale() {
        File file = resolveConfigFile();
        if (file != null && file.isFile()) {
            long mtime = file.lastModified();
            if (cache == null || mtime > lastLoadedMtime) {
                LOCK.lock();
                try {
                    if (cache == null || mtime > lastLoadedMtime) {
                        doReload();
                    }
                } finally {
                    LOCK.unlock();
                }
            }
            return;
        }
        if (cache == null) {
            LOCK.lock();
            try {
                if (cache == null) {
                    doReload();
                }
            } finally {
                LOCK.unlock();
            }
        }
    }

    private static void doReload() {
        File file = resolveConfigFile();
        Properties props = new Properties();
        String source;
        try {
            if (file != null && file.isFile()) {
                try (InputStream is = new FileInputStream(file);
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                lastLoadedMtime = file.lastModified();
                source = file.getAbsolutePath();
            } else {
                InputStream is = EinvoiceBossContractConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
                if (is == null) {
                    cache = Map.of();
                    log.warn("未找到 einvoice-boss-contract 配置文件: {}", CONFIG_FILE);
                    return;
                }
                try (InputStream stream = is;
                     InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                lastLoadedMtime = 0L;
                source = "classpath:" + CONFIG_FILE;
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 einvoice-boss-contract 配置失败", e);
        }
        cache = parseProperties(props);
        log.info("einvoice-boss-contract 已加载: source={}, contracts={}", source, cache.size());
    }

    private static Map<String, EinvoiceBossContractConfig> parseProperties(Properties props) {
        Map<String, EinvoiceBossContractConfig> result = new HashMap<>();
        Set<String> orgCodes = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(CONFIG_PREFIX)) {
                continue;
            }
            String part = key.substring(CONFIG_PREFIX.length());
            int dot = part.indexOf('.');
            if (dot > 0) {
                orgCodes.add(part.substring(0, dot));
            }
        }
        for (String orgCode : orgCodes) {
            EinvoiceBossContractConfig config = new EinvoiceBossContractConfig();
            config.setOrgCode(orgCode);
            config.setContractName(getProp(props, orgCode, "contract_name"));
            config.setFixmedinsCode(getProp(props, orgCode, "fixmedins_code"));
            config.setFixmedinsName(getProp(props, orgCode, "fixmedins_name"));
            config.setAppId(getProp(props, orgCode, "app_id"));
            config.setKey(getProp(props, orgCode, "key"));
            config.setUrl(getProp(props, orgCode, "url"));
            config.setMedicalInstitution(getProp(props, orgCode, "medical_institution"));
            config.setHospitalLv(getProp(props, orgCode, "hospital_lv"));
            config.setEnabled(!"false".equalsIgnoreCase(getProp(props, orgCode, "enabled")));
            result.put(orgCode, config);
        }
        return Map.copyOf(result);
    }

    private static String getProp(Properties props, String orgCode, String field) {
        return props.getProperty(CONFIG_PREFIX + orgCode + "." + field, "");
    }

    private static File resolveConfigFile() {
        String explicit = System.getProperty(CONFIG_PATH_PROP);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv(CONFIG_PATH_ENV);
        }
        if (explicit != null && !explicit.isBlank()) {
            return new File(explicit.trim());
        }
        try {
            URL url = EinvoiceBossContractConfigLoader.class.getClassLoader().getResource(CONFIG_FILE);
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                return new File(url.toURI());
            }
        } catch (Exception e) {
            log.debug("无法将 classpath:{} 解析为本地文件", CONFIG_FILE, e);
        }
        return null;
    }
}
