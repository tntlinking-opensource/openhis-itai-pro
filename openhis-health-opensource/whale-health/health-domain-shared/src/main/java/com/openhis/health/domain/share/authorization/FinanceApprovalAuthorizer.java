/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.authorization;

/**
 * 财务审批鉴权接口：动账类供应单 agree/reject 与调价审批共用。
 *
 * <p>实现由 {@code health-infrastructure/platform/authorization} 装配；领域写路径通过本接口委托，禁止在 Domain 内直查用户表。</p>
 */
public interface FinanceApprovalAuthorizer {

    /**
     * 当前登录用户是否具备财务审批权限（用户管理 {@code whale_user_roles} 上的 {@code ROLE_FINANCE_APPROVER}）。
     *
     * @return 可审批动账类单据时为 {@code true}
     */
    boolean hasFinanceApprovalRole();

    /**
     * 动账类供应单审批前门禁：{@code supplyTypeCode} 不在动账白名单时直接返回。
     *
     * @param supplyTypeCode 持久化 {@code wkf_supply_request.type_enum}
     * @throws com.openhis.whale.core.application.exception.BusinessException 动账类且当前用户无财务审批岗位时
     */
    void assertFinanceApprovalForSupplyType(String supplyTypeCode);
}
