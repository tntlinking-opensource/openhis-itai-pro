/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.enums;

/**
 * 收费明细（ChargeItem）事件类型。
 *
 * <p>用于 ChargeItem 写侧的 publishByType 路由分发，替代魔数。</p>
 *
 * <p>约定：1-8、16 为单条事件（逐条发布），10-15 为批量事件（首条代表批次）。</p>
 */
public enum ChargeItemEventType implements EnumInterface {

    /** 挂号项目已创建。 */
    REGISTRATION_CREATED(0L, "1", "挂号项目已创建", "", 1L, false),
    /** 医嘱草稿项目已创建。 */
    ORDER_DRAFT_CREATED(0L, "2", "医嘱草稿项目已创建", "", 2L, false),
    /** 护理计价项目已创建。 */
    NURSE_PRICING_CREATED(0L, "3", "护理计价项目已创建", "", 3L, false),
    /** 执行激活收费项目。 */
    ACTIVATED_BY_EXECUTION(0L, "4", "执行激活收费项目", "", 4L, false),
    /** 执行取消收费项目。 */
    EXECUTION_CANCELLED(0L, "5", "执行取消收费项目", "", 5L, false),
    /** 收费项目已结算。 */
    SETTLED(0L, "6", "收费项目已结算", "", 6L, false),
    /** 挂号项目已退费。 */
    REGISTRATION_REFUNDED(0L, "7", "挂号项目已退费", "", 7L, false),
    /** 半退保留链再生收费行已创建（新行 PLANNED，链回源 SETTLED 行）。 */
    PRESERVED_LINE_REGENERATED_CHARGE_CREATED(0L, "8", "半退保留链再生收费行已创建", "", 8L, false),

    /** 门诊账单退款申请。 */
    OUTPATIENT_BILL_REFUND_REQUESTED(0L, "10", "门诊账单退款申请", "", 10L, true),
    /** 门诊账单退款完成。 */
    OUTPATIENT_BILL_REFUND_COMPLETED(0L, "11", "门诊账单退款完成", "", 11L, true),
    /** 住院收费项目批量结算。 */
    INPATIENT_ITEMS_SETTLED(0L, "12", "住院收费项目批量结算", "", 12L, true),
    /** 住院账单退款申请。 */
    INPATIENT_BILL_REFUND_REQUESTED(0L, "13", "住院账单退款申请", "", 13L, true),
    /** 住院账单退款完成。 */
    INPATIENT_BILL_REFUND_COMPLETED(0L, "14", "住院账单退款完成", "", 14L, true),
    /** 住院收费项目批量反结回退。 */
    INPATIENT_ITEMS_REVERTED(0L, "15", "住院收费项目批量反结回退", "", 15L, true),
    /** 住院滚费收费项目已创建。 */
    AUTO_ROLL_CREATED(0L, "16", "住院滚费收费项目已创建", "", 16L, false);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;
    private final boolean batchEvent;

    ChargeItemEventType(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder,
        boolean batchEvent
    ) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
        this.batchEvent = batchEvent;
    }

    /**
     * 编码系统标识。
     *
     * @return codeSystemId
     */
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    /**
     * 事件类型码（String，枚举统一口径）。
     *
     * @return code
     */
    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

    /**
     * 获取整型码值（与历史常量保持一致）。
     *
     * <p>仅用于需要以 int 进行区间判断或对接遗留接口的场景；持久化与对外口径仍以 {@link #getCode()} 为准。</p>
     *
     * @return 整型码值
     */
    public int getIntCode() {
        return Integer.parseInt(this.code);
    }

    /**
     * 是否为批量粒度事件（10-14，不含 8）。
     *
     * @return 批量事件返回 {@code true}
     */
    public boolean isBatchEvent() {
        return batchEvent;
    }
}

