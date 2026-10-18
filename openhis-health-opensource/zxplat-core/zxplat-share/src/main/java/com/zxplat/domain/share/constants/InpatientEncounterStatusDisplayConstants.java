/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院（{@code IMP}）就诊状态产品展示文案（对齐 DM-ADM-002 §2.3.2、读模型 §2.1）。
 *
 * <p>持久化身份仍为 {@code EncounterStatus#getCode()} 字符串；本接口仅承载人读文案。
 * 禁止在 IMP 场景直接使用 {@link com.zxplat.domain.share.enums.EncounterStatus#getDisplay()}。</p>
 */
public interface InpatientEncounterStatusDisplayConstants {

    /** {@code PLANNED} → 待登记。 */
    String PLANNED = "待登记";

    /** {@code REGISTERED} → 待入科。 */
    String REGISTERED = "待入科";

    /** {@code IN_PROGRESS} → 已入科。 */
    String IN_PROGRESS = "已入科";

    /** {@code ON_HOLD} 且 {@code transfer_org_id} 非空 → 待转科。 */
    String ON_HOLD_TRANSFER = "待转科";

    /** {@code ON_HOLD} 且存在出院类型 → 待出院。 */
    String ON_HOLD_DISCHARGE = "待出院";

    /** {@code PENDING} → 待结算。 */
    String PENDING = "待结算";

    /** {@code COMPLETED} → 已结算。 */
    String COMPLETED = "已结算";

    /** {@code DISCHARGED} → 已出院。 */
    String DISCHARGED = "已出院";

    /** {@code CANCELLED} → 取消登记。 */
    String CANCELLED = "取消登记";

    /** {@code PRE_ADMISSION} → 预入院。 */
    String PRE_ADMISSION = "预入院";

    /** {@code ENTERED_IN_ERROR} → 错误录入。 */
    String ENTERED_IN_ERROR = "错误录入";
}
