/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.session;

import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.support.ChsGatewayOutputSupport;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;
import com.openhis.chs.gateway.config.ContractCredential;
import com.openhis.chs.gateway.session.transport.Sign9001Transport;
import com.openhis.chs.gateway.support.ChsSignEndpointSupport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 9001 签到与 sign_no 会话管理（全平台 infno 调用的前置门禁）。
 *
 * <p>职责：<。</p>
 * <ul>
 *   <li>内存 + {@code yb_pub_sign} 双层缓存，避免每次交易重复 9001</li>
 *   <li>OAuth Token 走契约配置；9001 的 opter 用 admin/admin1 等候选轮换</li>
 *   <li>MAC 解密失败时给出可读运维提示（读卡插件 / sign_mac 配置）</li>
 * </ul>
 *
 * <p>持久化经 {@link YbSignPersistenceService}；UoW 由 AppServiceAspect 管理。<。</p> */
@Service
@RequiredArgsConstructor
public class InsuranceSignSessionService {

    private static final Logger log = LoggerFactory.getLogger(InsuranceSignSessionService.class);

    private final Sign9001Transport sign9001Transport;
    private final YbSignPersistenceService signPersistenceService;
    private final ChsSignCredentialResolver signCredentialResolver;

    /** 进程内 sign_no 缓存，key 含 configEpoch+bus_no+adm+opter */
    private final Map<String, ChsSignSession> sessionCache = new ConcurrentHashMap<>();

    /**
     * 确保指定契约已签到；无有效 sign_no 时自动触发 {@link #signIn}。
     *
     * @param contractBusNo 契约 bus_no
     * @param context 操作员上下文（决定 sign opter 候选） */
    public void ensureSigned(String contractBusNo, InvokeContext context) {
        // Gate：无 bus_no 时跳过（由上层决定是否必填）
        if (contractBusNo == null || contractBusNo.isBlank()) {
            return;
        }
        String key = cacheKey(contractBusNo, context);
        // Pre：查内存缓存
        ChsSignSession cached = sessionCache.get(key);
        if (cached != null && cached.signNo() != null && !cached.signNo().isBlank()) {
            return;
        }
        // Pre：查当日 yb_pub_sign 持久化记录
        Optional<ChsSignSession> persisted = loadPersistedSession(contractBusNo, context);
        if (persisted.isPresent()) {
            sessionCache.put(key, persisted.get());
            log.debug("ensureSigned 复用持久化 sign_no: bus_no={}", contractBusNo);
            return;
        }
        // Cmd：调平台 9001
        signIn(contractBusNo, context);
    }

    /**
     * 获取当前有效 sign_no（须已签到或存在当日持久化记录）。
     *
     * @param contractBusNo 契约 bus_no
     * @param context 操作员上下文
     * @return 平台 sign_no；无会话时返回空字符串 */
    public String getSignNo(String contractBusNo, InvokeContext context) {
        return getActiveSession(contractBusNo, context).signNo();
    }

    /**
     * 获取当前签到会话（内存 → 持久化 → 空会话）。
     *
     * @param contractBusNo 契约 bus_no
     * @param context 操作员上下文
     * @return 含 signNo 与 signOpterNo 的会话快照 */
    public ChsSignSession getActiveSession(String contractBusNo, InvokeContext context) {
        if (contractBusNo == null || contractBusNo.isBlank()) {
            return new ChsSignSession("", null);
        }
        String key = cacheKey(contractBusNo, context);
        ChsSignSession cached = sessionCache.get(key);
        if (cached != null && cached.signNo() != null && !cached.signNo().isBlank()) {
            return cached;
        }
        return loadPersistedSession(contractBusNo, context)
            .map(session -> {
                sessionCache.put(key, session);
                return session;
            })
            .orElse(new ChsSignSession("", null));
    }

    /**
     * Token 始终走契约 OAuth 配置，不从签到会话注入。
     *
     * @return 原样返回 context（占位扩展点） */
    public InvokeContext enrichContextForToken(String contractBusNo, InvokeContext context, ContractConfig config) {
        return context;
    }

    /**
     * 【9001】执行医保签到；优先复用当日已持久化的 sign_no。
     *
     * @param contractBusNo 契约 bus_no
     * @param context 操作员与 MAC/IP 等签到参数来源
     * @return 平台 sign_no
     * @throws IllegalArgumentException 契约配置不存在
     * @throws IllegalStateException MAC 无效或平台拒绝 */
    public String signIn(String contractBusNo, InvokeContext context) {
        // Pre：加载 chs-contract 配置
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new IllegalArgumentException("未找到契约配置: bus_no=" + contractBusNo);
        }
        // Pre：当日已有持久化 sign_no 则直接复用
        Optional<ChsSignSession> persisted = loadPersistedSession(contractBusNo, context);
        if (persisted.isPresent()) {
            ChsSignSession session = persisted.get();
            sessionCache.put(cacheKey(contractBusNo, context), session);
            log.info("复用当日已持久化 sign_no: bus_no={} signOpter={}", contractBusNo, session.signOpterNo());
            return session.signNo();
        }

        try {
            return signInPlatform(contractBusNo, context, config);
        } catch (IllegalStateException ex) {
            // MAC 解密失败：提示读卡插件或更新 sign_mac，便于现场排查
            if (isMacDecryptError(ex.getMessage())) {
                throw new IllegalStateException(
                    "医保签到失败（MAC 无效）：请在前端通过读卡插件完成签到，"
                        + "或更新 chs-contract.properties 的 sign_mac 为 HeaSec 完整输出。"
                        + " 原始错误: " + ex.getMessage(), ex);
            }
            throw ex;
        }
    }

    /**
     * 循环尝试 sign opter 候选列表调用 9001；冲突/失败时轮换下一个 opter */
    private String signInPlatform(String contractBusNo, InvokeContext context, ContractConfig config) {
        ContractCredential oauth = signCredentialResolver.resolveOAuthCredential(config);
        List<String> signOpters = signCredentialResolver.resolveSignOpterCandidates(context);
        String lastError = null;
        // 遍历 admin/admin1 等签到操作员候选
        for (int i = 0; i < signOpters.size(); i++) {
            String signOpter = signOpters.get(i);
            Sign9001Input signIn = Sign9001Input.builder()
                .opterNo(signOpter)
                .mac(ChsSignEndpointSupport.resolveSignMac(config, context))
                .ip(ChsSignEndpointSupport.resolveSignIp(config, context))
                .build();

            InvokeContext signContext = withSignOpter(context, signOpter);
            ResultBody body = sign9001Transport.send(contractBusNo, signIn, signContext);
            if (body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
                String signNo = ChsGatewayOutputSupport.parseSignNo(body);
                if (signNo == null || signNo.isBlank()) {
                    throw new IllegalStateException("9001 签到失败: " + formatPlatformFailure(body));
                }
                ChsSignSession session = new ChsSignSession(signNo, signOpter);
                sessionCache.put(cacheKey(contractBusNo, context), session);
                signPersistenceService.saveSignIn(config, signOpter, signNo, oauth.username());
                log.info("9001 签到成功: bus_no={} signOpter={} oauth={}", contractBusNo, signOpter, oauth.username());
                return signNo;
            }
            lastError = formatPlatformFailure(body);
            // 240401 等「已签到/已签退」冲突：换下一个 opter 重试
            if (i + 1 < signOpters.size() && isSignConflictError(lastError)) {
                log.warn("签到操作员 {} 冲突({})，尝试 {}", signOpter, lastError, signOpters.get(i + 1));
                continue;
            }
            if (i + 1 < signOpters.size()) {
                log.warn("签到操作员 {} 失败({})，尝试 {}", signOpter, lastError, signOpters.get(i + 1));
                continue;
            }
            throw new IllegalStateException("医保平台调用失败: " + lastError);
        }
        throw new IllegalStateException("医保平台调用失败: " + formatLastSignError(lastError));
    }

    private static String formatLastSignError(String lastError) {
        if (lastError != null) {
            return lastError;
        }
        return "签到失败";
    }

    /**
     * 清除内存签到缓存（签退或契约热更新后调用）。
     *
     * @param contractBusNo 契约 bus_no
     * @param context 操作员上下文 */
    public void signOut(String contractBusNo, InvokeContext context) {
        sessionCache.remove(cacheKey(contractBusNo, context));
    }

    /** 从 yb_pub_sign 加载当日有效签到记录 */
    private Optional<ChsSignSession> loadPersistedSession(String contractBusNo, InvokeContext context) {
        return signPersistenceService.findTodaySession(
            contractBusNo, signCredentialResolver.resolvePrimarySignOpter(context));
    }

    private static InvokeContext withSignOpter(InvokeContext context, String signOpter) {
        InvokeContext.InvokeContextBuilder builder;
        if (context != null) {
            builder = context.toBuilder();
        } else {
            builder = InvokeContext.builder();
        }
        return builder
            .operatorId(signOpter)
            .operatorName(signOpter)
            .build();
    }

    /** 平台返回「操作员已签到/已签退」类错误，可换 opter 重试 */
    private static boolean isSignConflictError(String errMsg) {
        if (errMsg == null || errMsg.isBlank()) {
            return false;
        }
        return errMsg.contains("240401")
            || errMsg.contains("已经签退")
            || errMsg.contains("已经签到")
            || errMsg.contains("签退过了");
    }

    /** HeaSec MAC 解密失败，需读卡插件或更新 sign_mac */
    private static boolean isMacDecryptError(String errMsg) {
        if (errMsg == null || errMsg.isBlank()) {
            return false;
        }
        return errMsg.contains("解密Mac")
            || errMsg.contains("Mac地址")
            || errMsg.contains("encodeMac");
    }

    private static String formatPlatformFailure(ResultBody body) {
        if (body == null) {
            return "empty response";
        }
        if (body.getErrMsg() != null && !body.getErrMsg().isBlank()) {
            return body.getErrMsg();
        }
        if (body.getInfcode() != null && !body.getInfcode().isBlank()) {
            return "infcode=" + body.getInfcode();
        }
        return "未知错误（请检查医保 OAuth Token 与契约配置）";
    }

    /**
     * 缓存键含 configEpoch，契约热加载后自动失效旧 sign_no */
    private String cacheKey(String contractBusNo, InvokeContext context) {
        String signOpter = signCredentialResolver.resolvePrimarySignOpter(context);
        ContractConfig cfg = ContractConfigLoader.getByBusNo(contractBusNo);
        String adm = "";
        if (cfg != null && cfg.getAdmVs() != null) {
            adm = cfg.getAdmVs();
        }
        return ContractConfigLoader.getConfigEpoch() + ":" + contractBusNo + ":" + adm + ":" + signOpter;
    }
}
