/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.client.ChsFileUploadResult;
import com.zxplat.plugin.chs.gateway.config.ContractConfig;
import com.zxplat.plugin.chs.gateway.config.ContractConfigLoader;
import com.zxplat.plugin.chs.gateway.crypto.ChsSmCryptoService;
import com.zxplat.plugin.chs.gateway.support.InsuplcAdmdvsRule;
import com.zxplat.plugin.chs.gateway.session.ChsSignSession;
import com.zxplat.plugin.chs.gateway.session.ChsSignCredentialResolver;
import com.zxplat.plugin.chs.gateway.session.InsuranceSignSessionService;
import com.zxplat.plugin.chs.gateway.support.ChsResponseParser;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.zxplat.plugin.chs.gateway.support.ChsGatewayOutputSupport;
import com.zxplat.plugin.chs.gateway.support.MsgIdUtil;
import com.zxplat.plugin.chs.gateway.support.ServicePathConstants;
import com.zxplat.plugin.chs.gateway.support.TransBody;
import static com.zxplat.framework.core.application.util.StringUtils.ensureLeadingSlash;
import static com.zxplat.framework.core.application.util.StringUtils.positiveOrDefault;
import static com.zxplat.framework.core.application.util.StringUtils.stripTrailingSlash;
import com.zxplat.plugin.chs.domain.share.support.ChsTextSupport;
import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医保 HTTP 调用：组 TransBody、取 Token、POST、解析 ResultBody */
@Slf4j
@Component
public class ChsHttpAccessor {

    private final ChsSmCryptoService cryptoService;
    private final InsuranceSignSessionService signSessionService;
    private final ChsSignCredentialResolver signCredentialResolver;
    private static final int TOKEN_CACHE_FALLBACK_SECONDS = 3_500;

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public ChsHttpAccessor(ChsSmCryptoService cryptoService,
                           @Lazy InsuranceSignSessionService signSessionService,
                           ChsSignCredentialResolver signCredentialResolver) {
        this.cryptoService = cryptoService;
        this.signSessionService = signSessionService;
        this.signCredentialResolver = signCredentialResolver;
    }

    /** 组报文并 POST 调用医保平台，返回解析后的 {@link ResultBody} */
    public ResultBody send(String contractBusNo, String infNo, Object input, InvokeContext context) {
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new IllegalArgumentException("未找到契约配置: bus_no=" + contractBusNo);
        }
        InvokeContext effectiveContext = normalizeContext(context);
        String signNo;
        InvokeContext tokenContext = effectiveContext;
        if (ChsInfNo.SIGN_IN.equals(infNo)) {
            signNo = "";
        } else {
            signSessionService.ensureSigned(contractBusNo, effectiveContext);
            ChsSignSession session = signSessionService.getActiveSession(contractBusNo, effectiveContext);
            signNo = session.signNo();
            tokenContext = signSessionService.enrichContextForToken(contractBusNo, effectiveContext, config);
        }
        String insuplcAdmdvs = InsuplcAdmdvsRule.resolve(infNo, input, effectiveContext, config);
        String opterId = signCredentialResolver.resolveOperatorId(effectiveContext);
        String opterName = signCredentialResolver.resolveOperatorName(effectiveContext);

        String inputJson;
        if (input == null) {
            inputJson = "{}";
        } else {
            inputJson = JSON.toJSONString(input);
        }
        Object inputPayload;
        if (config.isEncrypt()) {
            inputPayload = cryptoService.encryptInput(inputJson, config);
        } else if (input instanceof String) {
            inputPayload = JSON.parse(inputJson);
        } else {
            inputPayload = input;
        }
        String cainfo = "";
        String signtype = "";
        if (config.isEncrypt()) {
            cainfo = cryptoService.signSm2(inputJson, config);
            signtype = "SM2";
        }

        TransBody trans = TransBody.builder()
            .infno(infNo)
            .msgid(MsgIdUtil.getMsgId(config.getFixmedinsCode()))
            .mdtrtareaAdmvs(config.getAdmVs())
            .insuplcAdmdvs(insuplcAdmdvs)
            .recerSysCode("his")
            .devNo("")
            .devSafeInfo("")
            .cainfo(cainfo)
            .signtype(signtype)
            .infver("V1.0")
            .opterType("1")
            .encType("SM4")
            .opter(opterId)
            .opterName(opterName)
            .infTime(DateUtil.now())
            .fixmedinsCode(config.getFixmedinsCode())
            .fixmedinsName(config.getFixmedinsName())
            .signNo(signNo)
            .input(inputPayload)
            .build();
        ChsInvokeLogContextHolder.merge(ctx -> ctx.setMsgid(trans.getMsgid()).setInfno(infNo));

        String url = resolveUrl(config.getCallServiceUrl(), config.getBaseUrl(), ServicePathConstants.CALL_SERVICE_PATH);
        String body = JSON.toJSONString(trans);
        log.info("医保请求 infNo={} bus_no={} url={}", infNo, contractBusNo, url);

        int timeout = positiveOrDefault(config.getHttpTimeoutMs(), 40_000);
        String response = HttpRequest.post(url)
            .addHeaders(getHeaders(config, tokenContext))
            .body(body)
            .timeout(timeout)
            .execute()
            .body();

        log.debug("医保响应 infNo={} response={}", infNo, response);
        ResultBody resultBody = ChsResponseParser.parseResultBody(response);
        if (resultBody != null && resultBody.getInfcode() != null
            && !ChsPlatformCodes.INFCODE_SUCCESS.equals(resultBody.getInfcode())) {
            log.warn("医保平台返回失败 infNo={} bus_no={} infcode={} err_msg={}",
                infNo, contractBusNo, resultBody.getInfcode(), resultBody.getErrMsg());
        }
        cryptoService.decryptOutputIfNeeded(resultBody, config);
        return resultBody;
    }

    /**
     * 【9102】文件下载：POST 至 fileUploadUrl，响应为 ZIP 二进制流 */
    public byte[] downloadFile(String contractBusNo, String infNo, Object input, InvokeContext context) {
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new IllegalArgumentException("未找到契约配置: bus_no=" + contractBusNo);
        }
        InvokeContext effectiveContext = normalizeContext(context);
        signSessionService.ensureSigned(contractBusNo, effectiveContext);
        String signNo = signSessionService.getSignNo(contractBusNo, effectiveContext);
        InvokeContext tokenContext = signSessionService.enrichContextForToken(contractBusNo, effectiveContext, config);
        String insuplcAdmdvs = InsuplcAdmdvsRule.resolve(infNo, input, effectiveContext, config);
        String opterId = signCredentialResolver.resolveOperatorId(effectiveContext);
        String opterName = signCredentialResolver.resolveOperatorName(effectiveContext);

        String inputJson;
        if (input == null) {
            inputJson = "{}";
        } else {
            inputJson = JSON.toJSONString(input);
        }
        Object inputPayload;
        if (config.isEncrypt()) {
            inputPayload = cryptoService.encryptInput(inputJson, config);
        } else if (input instanceof String) {
            inputPayload = JSON.parse(inputJson);
        } else {
            inputPayload = input;
        }
        String cainfo = "";
        String signtype = "";
        if (config.isEncrypt()) {
            cainfo = cryptoService.signSm2(inputJson, config);
            signtype = "SM2";
        }

        TransBody trans = TransBody.builder()
            .infno(infNo)
            .msgid(MsgIdUtil.getMsgId(config.getFixmedinsCode()))
            .mdtrtareaAdmvs(config.getAdmVs())
            .insuplcAdmdvs(insuplcAdmdvs)
            .recerSysCode("his")
            .devNo("")
            .devSafeInfo("")
            .cainfo(cainfo)
            .signtype(signtype)
            .infver("V1.0")
            .opterType("1")
            .encType("SM4")
            .opter(opterId)
            .opterName(opterName)
            .infTime(DateUtil.now())
            .fixmedinsCode(config.getFixmedinsCode())
            .fixmedinsName(config.getFixmedinsName())
            .signNo(signNo)
            .input(inputPayload)
            .build();

        String url = resolveUrl(config.getFileUploadUrl(), config.getBaseUrl(), ServicePathConstants.FILE_UPLOAD_PATH);
        String body = JSON.toJSONString(trans);
        log.info("医保文件下载 infNo={} bus_no={} url={}", infNo, contractBusNo, url);

        int timeout = positiveOrDefault(config.getHttpTimeoutMs(), 120_000);
        HttpResponse response = HttpRequest.post(url)
            .addHeaders(getHeaders(config, tokenContext))
            .body(body)
            .timeout(timeout)
            .execute();

        byte[] bytes = response.bodyBytes();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("9102 文件下载无响应体");
        }
        if (looksLikeJson(bytes)) {
            ResultBody resultBody = ChsResponseParser.parseResultBody(new String(bytes, StandardCharsets.UTF_8));
            cryptoService.decryptOutputIfNeeded(resultBody, config);
            String errMsg = ChsTextSupport.platformErrMsg(resultBody, "9102 下载失败");
            throw new IllegalStateException("9102 下载失败: " + errMsg);
        }
        return bytes;
    }

    /**
     * 【9101】文件上传：POST 至 fileUploadUrl，请求体为 TransBody，响应为 JSON */
    public ChsFileUploadResult uploadFile(
        String contractBusNo, byte[] zipBytes, String filename, InvokeContext context) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("9101 上传文件不能为空");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("9101 上传文件名不能为空");
        }
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new IllegalArgumentException("未找到契约配置: bus_no=" + contractBusNo);
        }
        InvokeContext effectiveContext = normalizeContext(context);
        signSessionService.ensureSigned(contractBusNo, effectiveContext);
        String signNo = signSessionService.getSignNo(contractBusNo, effectiveContext);
        InvokeContext tokenContext = signSessionService.enrichContextForToken(contractBusNo, effectiveContext, config);
        String opterId = signCredentialResolver.resolveOperatorId(effectiveContext);
        String opterName = signCredentialResolver.resolveOperatorName(effectiveContext);

        JSONObject fsUploadIn = new JSONObject();
        fsUploadIn.put("in", Base64.getEncoder().encodeToString(zipBytes));
        fsUploadIn.put("filename", filename.trim());
        fsUploadIn.put("fixmedins_code", config.getFixmedinsCode());
        JSONObject inputRoot = new JSONObject();
        inputRoot.put("fsUploadIn", fsUploadIn);
        String inputJson = inputRoot.toJSONString();

        String insuplcAdmdvs = InsuplcAdmdvsRule.resolve(ChsInfNo.FILE_UPLOAD, inputRoot, effectiveContext, config);
        Object inputPayload;
        if (config.isEncrypt()) {
            inputPayload = cryptoService.encryptInput(inputJson, config);
        } else {
            inputPayload = inputRoot;
        }
        String cainfo = "";
        String signtype = "";
        if (config.isEncrypt()) {
            cainfo = cryptoService.signSm2(inputJson, config);
            signtype = "SM2";
        }

        TransBody trans = TransBody.builder()
            .infno(ChsInfNo.FILE_UPLOAD)
            .msgid(MsgIdUtil.getMsgId(config.getFixmedinsCode()))
            .mdtrtareaAdmvs(config.getAdmVs())
            .insuplcAdmdvs(insuplcAdmdvs)
            .recerSysCode("his")
            .devNo("")
            .devSafeInfo("")
            .cainfo(cainfo)
            .signtype(signtype)
            .infver("V1.0")
            .opterType("1")
            .encType("SM4")
            .opter(opterId)
            .opterName(opterName)
            .infTime(DateUtil.now())
            .fixmedinsCode(config.getFixmedinsCode())
            .fixmedinsName(config.getFixmedinsName())
            .signNo(signNo)
            .input(inputPayload)
            .build();

        String url = resolveUrl(config.getFileUploadUrl(), config.getBaseUrl(), ServicePathConstants.FILE_UPLOAD_PATH);
        String body = JSON.toJSONString(trans);
        log.info("医保文件上传 infNo={} bus_no={} url={} filename={}", ChsInfNo.FILE_UPLOAD, contractBusNo, url, filename);

        int timeout = positiveOrDefault(config.getHttpTimeoutMs(), 120_000);
        String response = HttpRequest.post(url)
            .addHeaders(getHeaders(config, tokenContext))
            .body(body)
            .timeout(timeout)
            .execute()
            .body();

        ResultBody resultBody = ChsResponseParser.parseResultBody(response);
        cryptoService.decryptOutputIfNeeded(resultBody, config);
        if (resultBody == null || resultBody.getInfcode() == null
            || !ChsPlatformCodes.INFCODE_SUCCESS.equals(resultBody.getInfcode())) {
            String errMsg = ChsTextSupport.platformErrMsg(resultBody, "9101 上传失败");
            throw new IllegalStateException(errMsg);
        }
        JSONObject output = ChsGatewayOutputSupport.parseOutputObject(resultBody);
        ChsFileUploadResult result = new ChsFileUploadResult();
        result.setFileQuryNo(output.getString("file_qury_no"));
        result.setFilename(output.getString("filename"));
        result.setFixmedinsCode(output.getString("fixmedins_code"));
        result.setDldEndtime(output.getString("dld_endtime"));
        if (result.getFileQuryNo() == null || result.getFileQuryNo().isBlank()) {
            throw new IllegalStateException("9101 未返回 file_qury_no");
        }
        return result;
    }

    private static boolean looksLikeJson(byte[] bytes) {
        if (bytes.length == 0) {
            return false;
        }
        char first = (char) bytes[0];
        return first == '{' || first == '[';
    }

    private Map<String, String> getHeaders(ContractConfig config, InvokeContext context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + getToken(config, context));
        headers.put(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    /** Token 始终使用契约配置，与 9001 签到操作员无关 */
    private String resolveOauthUsername(ContractConfig config, InvokeContext context) {
        return config.getUsername();
    }

    private String resolveOauthPassword(ContractConfig config, InvokeContext context) {
        return config.getPassword();
    }

    private String getToken(ContractConfig config, InvokeContext context) {
        String username = resolveOauthUsername(config, context);
        String password = resolveOauthPassword(config, context);
        String key = "CHS_TOKEN:" + ContractConfigLoader.getConfigEpoch() + ":"
            + config.getAdmVs() + ":" + config.getBusNo() + ":" + username;
        CachedToken cached = tokenCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.token();
        }
        String url = resolveUrl(config.getTokenUrl(), config.getBaseUrl(), ServicePathConstants.TOKEN_PATH);
        Map<String, Object> form = new HashMap<>();
        form.put("username", username);
        form.put("password", password);
        form.put("scope", config.getScope());
        form.put("grant_type", config.getGrantType());
        String auth = Base64.getEncoder().encodeToString(
            (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + auth);
        headers.put(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

        String response = HttpRequest.post(url)
            .addHeaders(headers)
            .form(form)
            .timeout(10_000)
            .execute()
            .body();

        String token = ChsResponseParser.requireAccessToken(response);
        int expiresIn = ChsResponseParser.resolveExpiresInSeconds(response, TOKEN_CACHE_FALLBACK_SECONDS);
        long expireAtMs = System.currentTimeMillis() + Math.max(60L, expiresIn - 60L) * 1000L;
        tokenCache.put(key, new CachedToken(token, expireAtMs));
        return token;
    }

    private record CachedToken(String token, long expireAtMs) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expireAtMs;
        }
    }

    private static String resolveUrl(String explicitUrl, String baseUrl, String path) {
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return stripTrailingSlash(explicitUrl.trim());
        }
        return buildUrl(baseUrl, path);
    }

    private static String buildUrl(String baseUrl, String path) {
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (path == null) {
            path = "";
        }
        baseUrl = stripTrailingSlash(baseUrl);
        path = ensureLeadingSlash(path);
        return baseUrl + path;
    }

    private InvokeContext normalizeContext(InvokeContext context) {
        InvokeContext.InvokeContextBuilder builder;
        if (context != null) {
            builder = context.toBuilder();
        } else {
            builder = InvokeContext.builder();
        }
        if (context == null || context.getOperatorId() == null || context.getOperatorId().isBlank()) {
            builder.operatorId(signCredentialResolver.resolveOperatorId(context));
        }
        if (context == null || context.getOperatorName() == null || context.getOperatorName().isBlank()) {
            builder.operatorName(signCredentialResolver.resolveOperatorName(context));
        }
        return builder.build();
    }
}
