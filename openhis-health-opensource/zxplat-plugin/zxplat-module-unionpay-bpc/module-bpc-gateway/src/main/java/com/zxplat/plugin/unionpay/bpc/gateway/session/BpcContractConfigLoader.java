/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.gateway.session;

import com.zxplat.plugin.unionpay.bpc.gateway.config.BpcContractConfig;
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
 * 从 {@code unionpay-bpc-contract.properties} 按 {@code fixmedinsCode} 加载 BPC 终端配置。
 */
public final class BpcContractConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(BpcContractConfigLoader.class);

    private static final String CONFIG_FILE = "unionpay-bpc-contract.properties";
    private static final String CONFIG_PREFIX = "contract.";
    private static final String CONFIG_PATH_PROP = "unionpay.bpc.contract.config.path";
    private static final String CONFIG_PATH_ENV = "UNIONPAY_BPC_CONTRACT_CONFIG_PATH";

    private static final Lock LOCK = new ReentrantLock();

    private static volatile Map<String, BpcContractConfig> cache;
    private static volatile long configEpoch;
    private static volatile long lastLoadedMtime;
    private static volatile File watchedFile;
    private static volatile boolean watcherStarted;

    private BpcContractConfigLoader() {
    }

    public static long getConfigEpoch() {
        reloadIfStale();
        return configEpoch;
    }

    public static BpcContractConfig getByFixmedinsCode(String fixmedinsCode) {
        if (fixmedinsCode == null || fixmedinsCode.isBlank()) {
            return null;
        }
        reloadIfStale();
        return cache.get(fixmedinsCode.trim());
    }

    /**
     * 解析院点编码：请求已带则原样返回；否则在仅有一个 enabled contract 时回落为该编码。
     *
     * @param requested 调用方传入的 fixmedinsCode，可 null
     * @return 非空院点编码；无法唯一解析时 null
     */
    public static String resolveFixmedinsCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        reloadIfStale();
        if (cache == null || cache.isEmpty()) {
            return null;
        }
        java.util.List<String> enabled = cache.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue().isEnabled())
            .map(java.util.Map.Entry::getKey)
            .sorted()
            .toList();
        if (enabled.size() == 1) {
            return enabled.get(0);
        }
        return null;
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
                try (InputStream is = BpcContractConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (is == null) {
                        cache = Map.of();
                        configEpoch++;
                        log.warn("未找到 unionpay-bpc 配置文件: {}", CONFIG_FILE);
                        return;
                    }
                    props.load(is);
                }
                lastLoadedMtime = 0L;
                source = "classpath:" + CONFIG_FILE;
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 unionpay-bpc 配置失败", e);
        }
        cache = parseProperties(props);
        configEpoch++;
        log.info("unionpay-bpc-contract 已加载/热更新: source={}, contracts={}, epoch={}",
            source, cache.size(), configEpoch);
    }

    private static Map<String, BpcContractConfig> parseProperties(Properties props) {
        Map<String, BpcContractConfig> result = new HashMap<>();
        Set<String> fixmedinsCodes = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(CONFIG_PREFIX)) {
                continue;
            }
            String part = key.substring(CONFIG_PREFIX.length());
            int dot = part.indexOf('.');
            if (dot > 0) {
                fixmedinsCodes.add(part.substring(0, dot));
            }
        }
        for (String fixmedinsCode : fixmedinsCodes) {
            BpcContractConfig config = new BpcContractConfig();
            config.setFixmedinsCode(fixmedinsCode);
            config.setContractName(getProp(props, fixmedinsCode, "contract_name"));
            config.setEnabled("true".equalsIgnoreCase(getProp(props, fixmedinsCode, "enabled")));
            config.setTransport(getProp(props, fixmedinsCode, "transport"));
            config.setMid(getProp(props, fixmedinsCode, "mid"));
            config.setTid(getProp(props, fixmedinsCode, "tid"));
            config.setPosNo(getProp(props, fixmedinsCode, "pos_no"));
            config.setRequestUrl(getProp(props, fixmedinsCode, "request_url"));
            config.setMd5Secret(getProp(props, fixmedinsCode, "md5_secret"));
            config.setAcquirerProfile(getProp(props, fixmedinsCode, "acquirer_profile"));
            config.setSignVersion(getProp(props, fixmedinsCode, "sign_version"));
            String merTradeNoRule = getProp(props, fixmedinsCode, "mer_trade_no_rule");
            if (merTradeNoRule != null && !merTradeNoRule.isBlank()) {
                config.setMerTradeNoRule(merTradeNoRule.trim());
            }
            String queryTimeout = getProp(props, fixmedinsCode, "query_timeout_sec");
            if (queryTimeout != null && !queryTimeout.isBlank()) {
                try {
                    config.setQueryTimeoutSec(Integer.parseInt(queryTimeout.trim()));
                } catch (NumberFormatException ignored) {
                    config.setQueryTimeoutSec(300);
                }
            }
            String pollInterval = getProp(props, fixmedinsCode, "poll_interval_ms");
            if (pollInterval != null && !pollInterval.isBlank()) {
                try {
                    config.setPollIntervalMs(Integer.parseInt(pollInterval.trim()));
                } catch (NumberFormatException ignored) {
                    config.setPollIntervalMs(2000);
                }
            }
            result.put(fixmedinsCode, config);
        }
        return Map.copyOf(result);
    }

    private static String getProp(Properties props, String fixmedinsCode, String field) {
        return props.getProperty(CONFIG_PREFIX + fixmedinsCode + "." + field, "");
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
            URL url = BpcContractConfigLoader.class.getClassLoader().getResource(CONFIG_FILE);
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
        synchronized (BpcContractConfigLoader.class) {
            if (watcherStarted) {
                return;
            }
            watchedFile = file;
            Thread watcher = new Thread(BpcContractConfigLoader::watchLoop, "unionpay-bpc-contract-watcher");
            watcher.setDaemon(true);
            watcher.start();
            watcherStarted = true;
            log.info("unionpay-bpc-contract 文件监听已启动: {}", file.getAbsolutePath());
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
            log.warn("unionpay-bpc-contract 文件监听异常，仍可通过下次请求按 mtime 热加载: {}", e.getMessage());
        }
    }
}
