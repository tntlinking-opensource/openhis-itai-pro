/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import static com.zxplat.framework.core.application.util.StringUtils.firstNonBlank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.config.ContractConfig;
import cn.hutool.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 9001 签到终端信息，对齐平台：
 * <ul>
 *   <li>MAC 由 Zxplat.Win（FunctionId=5）或前端/配置传入</li>
 *   <li>URL 传参时空格需还原为 {@code +}（见 YbDao.getSignParam）</li>
 * </ul> */
public final class ChsSignEndpointSupport {

    private static final Logger log = LoggerFactory.getLogger(ChsSignEndpointSupport.class);

    private static final String DEFAULT_PLUGIN_URL = "http://127.0.0.1:5000/api/data/";

    private ChsSignEndpointSupport() {
    }

    /** 解析签到 MAC：上下文 → 读卡插件 → 契约配置（须形如 HeaSec Base64） */
    public static String resolveSignMac(ContractConfig config, InvokeContext context) {
        String contextMac = null;
        if (context != null) {
            contextMac = context.getSignMac();
        }
        String mac = firstNonBlank(contextMac);
        if (mac != null && isPlausibleSignMac(mac)) {
            return normalizeMac(mac);
        }
        mac = fetchMacFromYbPlugin(config);
        if (mac != null) {
            return mac;
        }
        String configMac = null;
        if (config != null) {
            configMac = config.getSignMac();
        }
        if (configMac != null && isPlausibleSignMac(configMac)) {
            return normalizeMac(configMac);
        }
        if (configMac != null && !configMac.isBlank()) {
            String busNo;
            if (config != null) {
                busNo = config.getBusNo();
            } else {
                busNo = "?";
            }
            log.warn("契约 sign_mac 长度/格式异常，已忽略: bus_no={} len={}",
                busNo, configMac.trim().length());
        }
        throw new IllegalStateException(
            "无法获取签到 MAC：请启动 Zxplat.Win 读卡插件并在前端签到，"
                + "或将 chs-contract.properties 中 sign_mac 配置为 HeaSec GetClientID 完整输出");
    }

    /** HeaSec 加密 MAC 通常为较长 Base64；过短片段会导致平台「解密Mac地址失败」 */
    static boolean isPlausibleSignMac(String mac) {
        if (mac == null) {
            return false;
        }
        String normalized = mac.trim().replace(" ", "+");
        if (normalized.length() < 16) {
            return false;
        }
        return normalized.chars().allMatch(ch ->
            (ch >= 'A' && ch <= 'Z')
                || (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '+'
                || ch == '/'
                || ch == '=');
    }

    /** 解析签到 IP：上下文 → 契约配置 → 读卡插件 → 本机 IPv4 */
    public static String resolveSignIp(ContractConfig config, InvokeContext context) {
        String contextIp = null;
        if (context != null) {
            contextIp = context.getSignIp();
        }
        String configIp = null;
        if (config != null) {
            configIp = config.getSignIp();
        }
        String ip = firstNonBlank(contextIp, configIp);
        if (ip != null) {
            return ip.trim();
        }
        ip = fetchIpFromYbPlugin(config);
        if (ip != null) {
            return ip.trim();
        }
        return detectLocalIpv4();
    }

    /** URL 解码后 Base64 中的 {@code +} 可能变成空格，与历史 YbDao 一致还原 */
    public static String normalizeMac(String mac) {
        return mac.trim().replace(" ", "+");
    }

    private static String fetchMacFromYbPlugin(ContractConfig config) {
        JSONObject body = buildPluginInitBody(config);
        body.put("FunctionId", 5);
        return postPlugin(config, body);
    }

    private static String fetchIpFromYbPlugin(ContractConfig config) {
        JSONObject body = new JSONObject();
        body.put("FunctionId", 6);
        return postPlugin(config, body);
    }

    private static JSONObject buildPluginInitBody(ContractConfig config) {
        JSONObject body = new JSONObject();
        body.put("IP", resolvePluginHost(config));
        body.put("PORT", resolvePluginPort(config));
        body.put("TIMEOUT", 60);
        body.put("LOG_PATH", "C:/neu_log/");
        body.put("SFZ_DRIVER_TYPE", 0);
        return body;
    }

    private static String postPlugin(ContractConfig config, JSONObject body) {
        String url;
        if (config != null && config.getYbPluginUrl() != null && !config.getYbPluginUrl().isBlank()) {
            url = config.getYbPluginUrl().trim();
        } else {
            url = DEFAULT_PLUGIN_URL;
        }
        try {
            String response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body.toJSONString())
                .timeout(5_000)
                .execute()
                .body();
            String parsed = parsePluginResponse(response);
            if (parsed == null || parsed.isBlank()) {
                return null;
            }
            if (body.getIntValue("FunctionId") == 5) {
                return normalizeMac(parsed);
            }
            return parsed.trim();
        } catch (Exception e) {
            log.debug("调用医保读卡插件失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    private static String parsePluginResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        String trimmed = response.trim();
        if (trimmed.startsWith("{")) {
            JSONObject jo = JSON.parseObject(trimmed);
            if (jo.containsKey("error")) {
                return null;
            }
            if (jo.containsKey("data")) {
                return jo.getString("data");
            }
        }
        return trimmed;
    }

    private static String resolvePluginHost(ContractConfig config) {
        if (config != null && config.getCardReaderHost() != null && !config.getCardReaderHost().isBlank()) {
            return config.getCardReaderHost().trim();
        }
        if (config != null && config.getBaseUrl() != null) {
            String base = config.getBaseUrl().trim();
            if (base.startsWith("http://")) {
                base = base.substring(7);
            } else if (base.startsWith("https://")) {
                base = base.substring(8);
            }
            int colon = base.indexOf(':');
            if (colon > 0) {
                return base.substring(0, colon);
            }
            return base;
        }
        return "ddjk.jlhs.gov.cn";
    }

    private static int resolvePluginPort(ContractConfig config) {
        if (config != null && config.getCardReaderPort() != null && config.getCardReaderPort() > 0) {
            return config.getCardReaderPort();
        }
        return 20215;
    }

    private static String detectLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni == null || ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
