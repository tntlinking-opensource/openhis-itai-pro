/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
 * 从 {@code chs-contract.properties} 按 {@code bus_no} 加载医保终端配置（唯一配置源）。
 * <p>{@code fin_contract} 仅提供与配置段对齐的 {@code bus_no}；本加载器不读费用性质表内
 * {@code fixmedins_code}、{@code adm_vs} 等列。<。</p>
 * <p>
 * 热加载：监听配置文件变更（修改时间或目录 Watch），自动刷新缓存并使 {@link #getConfigEpoch()} 递增，
 * 以便 Token / 签到缓存失效。
 * <p>
 * 配置文件解析顺序：
 * <ol>
 *   <li>系统属性 {@code chs.contract.config.path}</li>
 *   <li>环境变量 {@code CHS_CONTRACT_CONFIG_PATH}</li>
 *   <li>classpath 下 {@code chs-contract.properties} 对应的本地文件（开发态 target/classes）</li>
 *   <li>classpath 资源流（jar 内，仅启动时加载一次）</li>
 * </ol> */
public final class ContractConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ContractConfigLoader.class);

    private static final String CONFIG_FILE = "chs-contract.properties";
    private static final String CONFIG_PREFIX = "contract.";
    private static final String CONFIG_PATH_PROP = "chs.contract.config.path";
    private static final String CONFIG_PATH_ENV = "CHS_CONTRACT_CONFIG_PATH";

    private static final Lock LOCK = new ReentrantLock();

    private static volatile Map<String, ContractConfig> cache;
    private static volatile long configEpoch;
    private static volatile long lastLoadedMtime;
    private static volatile File watchedFile;
    private static volatile boolean watcherStarted;

    private ContractConfigLoader() {
    }

    /** 配置版本号，每次热加载成功后递增 */
    public static long getConfigEpoch() {
        reloadIfStale();
        return configEpoch;
    }

    public static ContractConfig getByBusNo(String contractBusNo) {
        if (contractBusNo == null || contractBusNo.isBlank()) {
            return null;
        }
        reloadIfStale();
        String busNo = contractBusNo.trim();
        ContractConfig exact = cache.get(busNo);
        if (exact != null) {
            return exact;
        }
        String suffix = "-" + busNo;
        ContractConfig suffixMatch = null;
        for (Map.Entry<String, ContractConfig> entry : cache.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(suffix)) {
                if (suffixMatch != null) {
                    throw new IllegalStateException(
                        "chs-contract 存在多个契约匹配 bus_no=" + busNo + ": " + suffixMatch.getBusNo()
                            + " 与 " + key);
                }
                suffixMatch = entry.getValue();
            }
        }
        return suffixMatch;
    }

    /**
     * 按当前契约的定点机构码，查找同机构下指定就医地区划（{@code adm_vs}）的兄弟通道。
     *
     * <p>优先 {@code {fixmedins_code}-{admVs}} 精确段；否则扫描同 {@code fixmedins_code} 且
     * {@code adm_vs} 匹配的唯一配置。用于 1101 异地失败后切省通道（如 229900）重试。</p>
     *
     * @param currentBusNo 当前合同 bus_no
     * @param targetAdmVs  目标就医地区划
     * @return 兄弟通道配置；当前已是目标区划时返回当前配置；找不到则 null
     */
    public static ContractConfig findSiblingByAdmVs(String currentBusNo, String targetAdmVs) {
        if (currentBusNo == null || currentBusNo.isBlank()
            || targetAdmVs == null || targetAdmVs.isBlank()) {
            return null;
        }
        ContractConfig current = getByBusNo(currentBusNo.trim());
        if (current == null) {
            return null;
        }
        String target = targetAdmVs.trim();
        if (target.equals(trimToEmpty(current.getAdmVs()))) {
            return current;
        }
        String fixmedins = trimToEmpty(current.getFixmedinsCode());
        if (!fixmedins.isEmpty()) {
            ContractConfig byBusNo = getByBusNo(fixmedins + "-" + target);
            if (byBusNo != null && target.equals(trimToEmpty(byBusNo.getAdmVs()))) {
                return byBusNo;
            }
        }
        reloadIfStale();
        ContractConfig match = null;
        for (ContractConfig candidate : cache.values()) {
            if (candidate == null) {
                continue;
            }
            if (!fixmedins.isEmpty()
                && !fixmedins.equals(trimToEmpty(candidate.getFixmedinsCode()))) {
                continue;
            }
            if (!target.equals(trimToEmpty(candidate.getAdmVs()))) {
                continue;
            }
            if (match != null
                && !trimToEmpty(match.getBusNo()).equals(trimToEmpty(candidate.getBusNo()))) {
                throw new IllegalStateException(
                    "chs-contract 存在多个同机构 adm_vs=" + target + " 契约: "
                        + match.getBusNo() + " 与 " + candidate.getBusNo());
            }
            match = candidate;
        }
        return match;
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    /** 强制重新加载（管理接口或测试可调用） */
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
                try (InputStream is = new FileInputStream(file);
                     Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    // Pre：chs-contract.properties 为 UTF-8；Properties#load(InputStream) 按 ISO-8859-1 会把院名读成乱码
                    props.load(reader);
                }
                lastLoadedMtime = file.lastModified();
                source = file.getAbsolutePath();
            } else {
                try (InputStream is = ContractConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                    if (is == null) {
                        cache = Map.of();
                        configEpoch++;
                        log.warn("未找到 chs-contract 配置文件: {}", CONFIG_FILE);
                        return;
                    }
                    try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        props.load(reader);
                    }
                }
                lastLoadedMtime = 0L;
                source = "classpath:" + CONFIG_FILE;
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 chs-contract 配置失败", e);
        }
        cache = parseProperties(props);
        configEpoch++;
        log.info("chs-contract 已加载/热更新: source={}, contracts={}, epoch={}",
            source, cache.size(), configEpoch);
    }

    private static Map<String, ContractConfig> parseProperties(Properties props) {
        Map<String, ContractConfig> result = new HashMap<>();
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
            ContractConfig c = new ContractConfig();
            c.setBusNo(busNo);
            c.setContractName(getProp(props, busNo, "contract_name"));
            c.setAdmVs(getProp(props, busNo, "adm_vs"));
            c.setCliPrvKey(getProp(props, busNo, "cli_prv_key"));
            c.setCliPubKey(getProp(props, busNo, "cli_pub_key"));
            c.setServerPubKey(getProp(props, busNo, "server_pub_key"));
            c.setScope(getProp(props, busNo, "scope"));
            c.setGrantType(getProp(props, busNo, "grant_type"));
            c.setPassword(getProp(props, busNo, "password"));
            c.setUsername(getProp(props, busNo, "username"));
            c.setClientSecret(getProp(props, busNo, "client_secret"));
            c.setClientId(getProp(props, busNo, "client_id"));
            c.setFixmedinsCode(getProp(props, busNo, "fixmedins_code"));
            c.setFixmedinsName(getProp(props, busNo, "fixmedins_name"));
            c.setBaseUrl(getProp(props, busNo, "base_url"));
            c.setTokenUrl(getProp(props, busNo, "token_url"));
            c.setCallServiceUrl(getProp(props, busNo, "call_service_url"));
            c.setFileUploadUrl(getProp(props, busNo, "file_upload_url"));
            c.setCardReaderHost(getProp(props, busNo, "card_reader_host"));
            String cardPort = getProp(props, busNo, "card_reader_port");
            if (cardPort != null && !cardPort.isBlank()) {
                try {
                    c.setCardReaderPort(Integer.parseInt(cardPort.trim()));
                } catch (NumberFormatException ignored) {
                    // 非法端口留空
                }
            }
            c.setEcVoucherUrl(getProp(props, busNo, "ec_voucher_url"));
            c.setSignMac(getProp(props, busNo, "sign_mac"));
            c.setSignIp(getProp(props, busNo, "sign_ip"));
            c.setYbPluginUrl(getProp(props, busNo, "yb_plugin_url"));
            c.setEncrypt("true".equalsIgnoreCase(getProp(props, busNo, "encrypt")));
            String timeout = getProp(props, busNo, "http_timeout_ms");
            if (timeout != null && !timeout.isBlank()) {
                try {
                    c.setHttpTimeoutMs(Integer.parseInt(timeout.trim()));
                } catch (NumberFormatException ignored) {
                    c.setHttpTimeoutMs(40_000);
                }
            }
            String contractIdStr = getProp(props, busNo, "contract_id");
            if (contractIdStr != null && !contractIdStr.isBlank()) {
                try {
                    c.setContractId(Long.parseLong(contractIdStr.trim()));
                } catch (NumberFormatException ignored) {
                    // 可选
                }
            }
            // 挂号费 2203 默认诊断（可选；覆盖 ChsOutpatientRegisterFeeConstants）
            c.setRegisterFeeDefaultDiagCode(getProp(props, busNo, "register_fee_default_diag_code"));
            c.setRegisterFeeDefaultDiagName(getProp(props, busNo, "register_fee_default_diag_name"));
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
            URL url = ContractConfigLoader.class.getClassLoader().getResource(CONFIG_FILE);
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
        synchronized (ContractConfigLoader.class) {
            if (watcherStarted) {
                return;
            }
            watchedFile = file;
            Thread watcher = new Thread(ContractConfigLoader::watchLoop, "chs-contract-watcher");
            watcher.setDaemon(true);
            watcher.start();
            watcherStarted = true;
            log.info("chs-contract 文件监听已启动: {}", file.getAbsolutePath());
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
            log.warn("chs-contract 文件监听异常，仍可通过下次请求按 mtime 热加载: {}", e.getMessage());
        }
    }
}
