package com.openhis.whale.report.application.support;

import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * report 模块操作人解析：封装 {@link ICurrentUser}，禁止 AppService 直注登录上下文（对齐 B.6.5）。
 */
@Component
public class ReportOperatorResolver {

    private final ICurrentUser currentUser;

    public ReportOperatorResolver(@Autowired(required = false) ICurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * 当前登录用户主键；匿名或未装配 identity 时为 {@code null}。
     */
    public Long findOperatorIdOrNull() {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return null;
        }
        return currentUser.getCurrentUserId();
    }

    /**
     * 当前登录用户显示名；匿名时为 {@code null}。
     */
    public String findOperatorNameOrNull() {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return null;
        }
        return currentUser.getCurrentUserName();
    }

    /**
     * 当前登录用户所属机构；匿名时为 {@code null}。
     */
    public Long findOrganizationIdOrNull() {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return null;
        }
        return currentUser.getCurrentOrganizationUnitId();
    }
}
