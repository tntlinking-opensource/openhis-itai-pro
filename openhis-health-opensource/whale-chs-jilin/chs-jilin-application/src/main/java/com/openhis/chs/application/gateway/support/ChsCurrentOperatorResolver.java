/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway.support;

import com.openhis.chs.application.config.ChsDeploymentProperties;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CHS 应用层操作人解析：嵌入 HIS 时从登录上下文取主键；独立 chs-server 固定 operatorId=1。
 *
 * <p>对齐《代码生成通用规范》B.6.5——{@code *AppService} 禁止直注 {@link ICurrentUser}，
 * 须经本组件或 {@code ChsSignCredentialResolver} 解析。</p>
 */
@Component
@RequiredArgsConstructor
public class ChsCurrentOperatorResolver {

    /** 独立部署时的固定操作人主键（与 9001 opter 约定一致）。 */
    private static final long STANDALONE_OPERATOR_ID = 1L;

    private final ICurrentUser currentUser;
    private final ChsDeploymentProperties deploymentProperties;

    /**
     * 要求当前请求已绑定登录用户（独立部署跳过校验，返回固定 id）。
     *
     * @param message 未登录时透出给调用方的中文提示
     * @return 非 {@code null} 的操作人主键
     * @throws BusinessException 嵌入模式且无登录用户时
     */
    public Long requireOperatorId(String message) {
        return requireOperatorId(ChsErrorCodes.CONTRACT_INVALID, message);
    }

    /**
     * 要求当前请求已绑定登录用户，可指定错误码。
     *
     * @param errorCode 业务错误码
     * @param message   未登录提示
     * @return 非 {@code null} 的操作人主键
     * @throws BusinessException 嵌入模式且无登录用户时
     */
    public Long requireOperatorId(String errorCode, String message) {
        if (deploymentProperties.isStandalone()) {
            return STANDALONE_OPERATOR_ID;
        }
        Long uid = currentUser.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException(errorCode, message);
        }
        return uid;
    }

    /**
     * 返回当前操作人主键；未登录时为 {@code null}（独立部署返回固定 id）。
     *
     * <p>供可选登录场景（如内部探测）；写路径须使用 {@link #requireOperatorId}。</p>
     *
     * @return 操作人主键，或 {@code null}
     */
    public Long findOperatorIdOrNull() {
        if (deploymentProperties.isStandalone()) {
            return STANDALONE_OPERATOR_ID;
        }
        return currentUser.getCurrentUserId();
    }
}
