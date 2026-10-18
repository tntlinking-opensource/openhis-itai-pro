/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial.cashiersettlement;

/**
 * 交账列表行：可批量回填交账人 / 审批人 / 药房 / 科室展示名。
 *
 * <p>审批列表、交账历史等共用，避免各 Feature 重复 enrich。</p>
 *
 * <p>setter 使用 {@code void}（勿用链式返回），避免 Lombok {@code @Accessors(chain=true)}
 * 与接口协变桥接在部分运行时出现 {@code AbstractMethodError}。</p>
 */
public interface CashierSettlementPartyNamedRow {

    /**
     * @return 交账人（提交人）主键；可空
     */
    Long getSubmitterId();

    /**
     * @return 审批人主键；可空
     */
    Long getApproverId();

    /**
     * @return 交账药房主键；可空
     */
    Long getLocationId();

    /**
     * @return 交账科室主键；可空
     */
    Long getOrgId();

    /**
     * 写入交账人姓名。
     *
     * @param submitterName 姓名；可空
     */
    void setSubmitterName(String submitterName);

    /**
     * 写入审批人姓名。
     *
     * @param approverName 姓名；可空
     */
    void setApproverName(String approverName);

    /**
     * 写入药房名称。
     *
     * @param locationName 名称；可空
     */
    void setLocationName(String locationName);

    /**
     * 写入科室名称。
     *
     * @param orgName 名称；可空
     */
    void setOrgName(String orgName);
}
