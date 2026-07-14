/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 从 {@code elep-contract.properties} 按 {@code bus_no} 加载电子处方终端配置。
 */
public final class ElepContractConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ElepContractConfigLoader.class);

    private static final String CONFIG_FILE = "elep-contract.properties";
    private static final String CONFIG_PREFIX = "contract.";
    private static final String CONFIG_PATH_PROP = "elep.contract.config.path";
    private static final String CONFIG_PATH_ENV = "ELEP_CONTRACT_CONFIG_PATH";

    private static final Lock LOCK = new ReentrantLock();

    private static volatile Map<String, ElepContractConfig> cache;
    private static volatile long configEpoch;
    private static volatile long lastLoadedMtime;
    private static volatile File watchedFile;
    private static volatile boolean watcherStarted;

    private ElepContractConfigLoader() {
    }

    public static long getConfigEpoch() {
        reloadIfStale();
        return configEpoch;
    }

    public static ElepContractConfig getByBusNo(String contractBusNo) {
        if (contractBusNo == null || contractBusNo.isBlank()) {
            return null;
        }
        reloadIfStale();
        String busNo = contractBusNo.trim();
        ElepContractConfig exact = cache.get(busNo);
        if (exact != null) {
            return exact;
        }
        String suffix = "-" + busNo;
        ElepContractConfig suffixMatch = null;
        for (Map.Entry<String, ElepContractConfig> entry : cache.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(suffix)) {
                if (suffixMatch != null) {
                    throw new IllegalStateException(
                            "elep-contract 存在多个契约匹配 bus_no=" + busNo + ": "
                                    + suffixMatch.getBusNo() + " 与 " + key);
                }
                suffixMatch = entry.getValue();
            }
        }
        return suffixMatch;
    }

    public static void refresh() {
        LOCK.lock();
        try {
            doReload();
        } finally {
            LOCK.unlock();
        }
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
            startFileWatcherIfNeeded(file);
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
                try (InputStream is = new FileInputStream(file)) {
                    props.load(is);
                }
                lastLoadedMtime = file.lastModified();
                source = file.getAbsolutePath();
            } else {
                try (InputStream is = ElepContractConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (is == null) {
                        cache = Map.of();
                        configEpoch++;
                        log.warn("未找到 elep-contract 配置文件: {}", CONFIG_FILE);
                        return;
                    }
                    props.load(is);
                }
                lastLoadedMtime = 0L;
                source = "classpath:" + CONFIG_FILE;
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 elep-contract 配置失败", e);
        }
        cache = parseProperties(props);
        configEpoch++;
        log.info("elep-contract 已加载/热更新: source={}, contracts={}, epoch={}",
                source, cache.size(), configEpoch);
    }

    private static Map<String, ElepContractConfig> parseProperties(Properties props) {
        Map<String, ElepContractConfig> result = new HashMap<>();
        Set<String> busNos = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(CONFIG_PREFIX)) {
                continue;
            }
            String part = key.substring(CONFIG_PREFIX.length());
            int dot = part.indexOf('.');
            if (dot > 0) {
                busNos.add(part.substring(0, dot));
            }
        }
        for (String busNo : busNos) {
            ElepContractConfig c = new ElepContractConfig();
            c.setBusNo(busNo);
            c.setContractName(getProp(props, busNo, "contract_name"));
            c.setFixmedinsCode(getProp(props, busNo, "fixmedins_code"));
            c.setFixmedinsName(getProp(props, busNo, "fixmedins_name"));
            c.setAdmVs(getProp(props, busNo, "adm_vs"));
            c.setEleBaseUrl(getProp(props, busNo, "ele_base_url"));
            c.setPreAppId(getProp(props, busNo, "pre_app_id"));
            c.setPreAppSecret(getProp(props, busNo, "pre_app_secret"));
            c.setPrePrvKey(getProp(props, busNo, "pre_prv_key"));
            c.setPrePubKey(getProp(props, busNo, "pre_pub_key"));
            c.setClientId(getProp(props, busNo, "client_id"));
            c.setClientSecret(getProp(props, busNo, "client_secret"));
            c.setUsername(getProp(props, busNo, "username"));
            c.setPassword(getProp(props, busNo, "password"));
            c.setScope(getProp(props, busNo, "scope"));
            c.setGrantType(getProp(props, busNo, "grant_type"));
            c.setTemplatePath(getProp(props, busNo, "template_path"));
            c.setOutputPath(getProp(props, busNo, "output_path"));
            c.setHospitalSealPath(getProp(props, busNo, "hospital_seal_path"));
            c.setEnabled(!"false".equalsIgnoreCase(getProp(props, busNo, "enabled")));
            String timeout = getProp(props, busNo, "http_timeout_ms");
            if (timeout != null && !timeout.isBlank()) {
                try {
                    c.setHttpTimeoutMs(Integer.parseInt(timeout.trim()));
                } catch (NumberFormatException ignored) {
                    c.setHttpTimeoutMs(40_000);
                }
            }
            String cacheEnabled = getProp(props, busNo, "catalog_cache_enabled");
            if (cacheEnabled != null && !cacheEnabled.isBlank()) {
                c.setCatalogCacheEnabled(!"false".equalsIgnoreCase(cacheEnabled.trim()));
            }
            String ttl = getProp(props, busNo, "catalog_cache_ttl_minutes");
            if (ttl != null && !ttl.isBlank()) {
                try {
                    c.setCatalogCacheTtlMinutes(Integer.parseInt(ttl.trim()));
                } catch (NumberFormatException ignored) {
                    c.setCatalogCacheTtlMinutes(30);
                }
            }
            String maxEntries = getProp(props, busNo, "catalog_search_cache_max_entries");
            if (maxEntries != null && !maxEntries.isBlank()) {
                try {
                    c.setCatalogSearchCacheMaxEntries(Integer.parseInt(maxEntries.trim()));
                } catch (NumberFormatException ignored) {
                    c.setCatalogSearchCacheMaxEntries(5000);
                }
            }
            result.put(busNo, c);
        }
        return Map.copyOf(result);
    }

    private static String getProp(Properties props, String busNo, String field) {
        return props.getProperty(CONFIG_PREFIX + busNo + "." + field, "");
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
            URL url = ElepContractConfigLoader.class.getClassLoader().getResource(CONFIG_FILE);
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                return new File(url.toURI());
            }
        } catch (Exception e) {
            log.debug("无法将 classpath:{} 解析为本地文件，热加载将仅依赖显式路径", CONFIG_FILE, e);
        }
        return null;
    }

    private static void startFileWatcherIfNeeded(File file) {
        if (watcherStarted) {
            return;
        }
        synchronized (ElepContractConfigLoader.class) {
            if (watcherStarted) {
                return;
            }
            watchedFile = file;
            Thread watcher = new Thread(ElepContractConfigLoader::watchLoop, "elep-contract-watcher");
            watcher.setDaemon(true);
            watcher.start();
            watcherStarted = true;
            log.info("elep-contract 文件监听已启动: {}", file.getAbsolutePath());
        }
    }

    private static void watchLoop() {
        File file = watchedFile;
        if (file == null) {
            return;
        }
        Path dir = file.getParentFile().toPath();
        String fileName = file.getName();
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Object ctx = event.context();
                    if (ctx instanceof Path changed && fileName.equals(changed.toString())) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        refresh();
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("elep-contract 文件监听异常，仍可通过下次请求按 mtime 热加载: {}", e.getMessage());
        }
    }
}
