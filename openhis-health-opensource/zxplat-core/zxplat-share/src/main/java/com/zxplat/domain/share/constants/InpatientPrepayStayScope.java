/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院预交金队列在院范围筛选码（IF-IP-PREP-01 {@code stayScope}）。
 *
 * <p>队列可读含 {@code COMPLETED}/{@code DISCHARGED}/{@code CANCELLED}（取消登记/退院）；
 * 收退写仍仅四态（见 BR-IP-PREP-001）。</p>
 */
public interface InpatientPrepayStayScope {

    /** 在院可收退：REGISTERED / IN_PROGRESS / ON_HOLD / PENDING。 */
    String IN_HOSPITAL = "IN_HOSPITAL";

    /**
     * 不在院只读：COMPLETED（待清床）+ DISCHARGED（已出院）+ CANCELLED（取消登记/退院）；
     * 读侧另要求 EXISTS 已收口预交金流水（无流水不入队）。
     */
    String NOT_IN_HOSPITAL = "NOT_IN_HOSPITAL";

    /**
     * 全部：在院四态直接入队；COMPLETED/DISCHARGED/CANCELLED 须有预交金流水。
     */
    String ALL = "ALL";
}
