/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院预交金队列在院范围筛选码（IF-IP-PREP-01 {@code stayScope}）。
 *
 * <p>队列可读含 {@code DISCHARGED}；收退写仍仅四态（见 BR-IP-PREP-001）。</p>
 */
public interface InpatientPrepayStayScope {

    /** 在院可收退：REGISTERED / IN_PROGRESS / ON_HOLD / PENDING。 */
    String IN_HOSPITAL = "IN_HOSPITAL";

    /** 不在院：COMPLETED（待清床）+ DISCHARGED（已清床离院）。 */
    String NOT_IN_HOSPITAL = "NOT_IN_HOSPITAL";

    /** 全部：在院四态 + COMPLETED + DISCHARGED。 */
    String ALL = "ALL";
}
