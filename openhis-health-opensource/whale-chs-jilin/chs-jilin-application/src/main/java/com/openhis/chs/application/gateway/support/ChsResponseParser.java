package com.openhis.chs.application.gateway.support;

import static com.openhis.whale.core.application.util.StringUtils.defaultIfBlank;
import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ResultBody;

/**
 * 医保 HTTP 响应解析：兼容 FSI {@link ResultBody} 与 OAuth / 网关错误体。
 */
public final class ChsResponseParser {

    private ChsResponseParser() {
    }

    public static ResultBody parseResultBody(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        JSONObject root = JSON.parseObject(response);
        if (root == null || root.isEmpty()) {
            return null;
        }
        if (root.containsKey("error") && !root.containsKey("infcode")) {
            ResultBody oauthError = new ResultBody();
            oauthError.setInfcode("-1");
            oauthError.setErrMsg(formatOAuthError(root));
            return oauthError;
        }
        ResultBody body = root.to(ResultBody.class);
        Object output = root.get("output");
        if (output != null && !(output instanceof String)) {
            body.setOutput(JSON.toJSONString(output));
        }
        if (body.getErrMsg() == null || body.getErrMsg().isBlank()) {
            String msg = firstNonBlank(root.getString("msg"), root.getString("message"));
            if (msg != null) {
                body.setErrMsg(msg);
            }
        }
        return body;
    }

    public static String requireAccessToken(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("医保 OAuth 无响应");
        }
        JSONObject root = JSON.parseObject(response);
        if (root == null) {
            throw new IllegalStateException("医保 OAuth 响应无法解析");
        }
        String token = root.getString("access_token");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }
        throw new IllegalStateException("医保 OAuth 获取 Token 失败: " + formatOAuthError(root));
    }

    public static int resolveExpiresInSeconds(String response, int defaultSeconds) {
        if (response == null || response.isBlank()) {
            return defaultSeconds;
        }
        JSONObject root = JSON.parseObject(response);
        if (root == null) {
            return defaultSeconds;
        }
        Integer expiresIn = root.getInteger("expires_in");
        if (expiresIn == null || expiresIn <= 0) {
            return defaultSeconds;
        }
        return expiresIn;
    }

    private static String formatOAuthError(JSONObject root) {
        String error = root.getString("error");
        String description = root.getString("error_description");
        String msg = root.getString("msg");
        String errMsg = root.getString("err_msg");
        if (error != null && description != null && !description.isBlank() && !"null".equalsIgnoreCase(description)) {
            return error + ": " + description;
        }
        if (error != null && (description == null || description.isBlank() || "null".equalsIgnoreCase(description))) {
            if ("invalid_token".equals(error)) {
                return "invalid_token（Access Token 无效或已过期，请重试签到）";
            }
            return error;
        }
        return defaultIfBlank(firstNonBlank(msg, errMsg, error, root.toJSONString()), "unknown");
    }
}
