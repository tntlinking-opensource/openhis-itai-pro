package com.openhis.chs.application.gateway.session;

import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.application.config.ChsDeploymentProperties;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractCredential;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 签到与经办人解析：OAuth Token 用契约配置；9001 签到 opter_no 用登录名（默认 admin，失败试 admin1）。
 */
@Component
public class ChsSignCredentialResolver {

    private static final String DEFAULT_SIGN_OPTER = "admin";

    private final ChsDeploymentProperties deploymentProperties;
    private final ICurrentUser currentUser;

    public ChsSignCredentialResolver(ChsDeploymentProperties deploymentProperties, ICurrentUser currentUser) {
        this.deploymentProperties = deploymentProperties;
        this.currentUser = currentUser;
    }

    /** OAuth Token 账号：始终取自契约配置。 */
    public ContractCredential resolveOAuthCredential(ContractConfig config) {
        if (config.getPassword() == null || config.getPassword().isBlank()) {
            throw new IllegalStateException("契约未配置 OAuth 密码");
        }
        if (config.getUsername() == null || config.getUsername().isBlank()) {
            throw new IllegalStateException("契约未配置 OAuth 用户名");
        }
        return new ContractCredential(config.getUsername().trim(), config.getPassword());
    }

    /** 9001 签到主操作员编号（signIn.opter_no / 持久化键）。 */
    public String resolvePrimarySignOpter(InvokeContext context) {
        return resolveSignOpterCandidates(context).get(0);
    }

    /**
     * 9001 签到操作员候选：嵌入 HIS 用当前登录用户名（默认 admin），独立 chs-server 固定 admin；失败时试 +1。
     */
    public List<String> resolveSignOpterCandidates(InvokeContext context) {
        String primary = resolvePrimarySignOpterName(context);
        List<String> candidates = new ArrayList<>(2);
        candidates.add(primary);
        String alternate = primary + "1";
        if (!alternate.equals(primary)) {
            candidates.add(alternate);
        }
        return candidates;
    }

    private String resolvePrimarySignOpterName(InvokeContext context) {
        if (deploymentProperties.isStandalone()) {
            return DEFAULT_SIGN_OPTER;
        }
        if (context != null && context.getOperatorName() != null && !context.getOperatorName().isBlank()) {
            return context.getOperatorName().trim();
        }
        String fromUser = currentUser.getCurrentUserName();
        if (fromUser != null && !fromUser.isBlank()) {
            return fromUser.trim();
        }
        return DEFAULT_SIGN_OPTER;
    }

    /** 业务报文经办人展示名（TransBody.opter_name），独立部署固定 admin。 */
    public String resolveOperatorName(InvokeContext context) {
        if (deploymentProperties.isStandalone()) {
            return DEFAULT_SIGN_OPTER;
        }
        if (context != null && context.getOperatorName() != null && !context.getOperatorName().isBlank()) {
            return context.getOperatorName().trim();
        }
        String fromUser = currentUser.getCurrentUserName();
        if (fromUser != null && !fromUser.isBlank()) {
            return fromUser.trim();
        }
        return DEFAULT_SIGN_OPTER;
    }

    /** 业务报文经办人 ID（TransBody.opter），独立部署固定 1。 */
    public String resolveOperatorId(InvokeContext context) {
        if (deploymentProperties.isStandalone()) {
            return "1";
        }
        if (context != null && context.getOperatorId() != null && !context.getOperatorId().isBlank()) {
            return context.getOperatorId();
        }
        Long uid = currentUser.getCurrentUserId();
        if (uid != null) {
            return String.valueOf(uid);
        }
        return "1";
    }
}
