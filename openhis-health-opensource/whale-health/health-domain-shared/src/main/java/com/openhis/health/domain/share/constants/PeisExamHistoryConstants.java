/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 体检历史共享常量（查看/取消）。
 */
public final class PeisExamHistoryConstants {

    /** 在检中文展示（就诊枚举 {@code IN_PROGRESS} 门诊口径为「在诊」）。 */
    public static final String STATUS_TEXT_IN_PROGRESS = "在检";

    /** 取消服务申请原因。 */
    public static final String CANCEL_REASON = "体检历史取消";

    /** 未登录。 */
    public static final String MSG_LOGIN = "请先登录后再取消体检";

    /** 就诊主键缺失。 */
    public static final String MSG_ENCOUNTER_REQUIRED = "取消体检失败：就诊标识不能为空";

    /** 非健康体检就诊。 */
    public static final String MSG_NOT_PE = "取消体检失败：仅健康体检就诊可取消";

    /** 已收费或费用已入账。 */
    public static final String MSG_PAID = "已收费，请先退费后再取消体检";

    /** 总检已定稿。 */
    public static final String MSG_FINAL = "总检已定稿，不能取消";

    /** 非在检。 */
    public static final String MSG_NOT_IN_PROGRESS = "仅在检中的体检可取消";

    /** 团检已真实收费建单。 */
    public static final String MSG_GROUP_PAID = "团检已收费，请先退费后再取消";

    /** 服务申请状态不允许取消。 */
    public static final String MSG_SR_NOT_CANCELABLE = "存在暂挂或已停嘱的检查项目，不能取消";

    private PeisExamHistoryConstants() {
    }
}
