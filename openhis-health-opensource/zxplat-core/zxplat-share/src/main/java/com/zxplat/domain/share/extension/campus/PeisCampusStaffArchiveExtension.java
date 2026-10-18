/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.campus;

/**
 * PEIS 团检名册 → Campus 教职工档案写侧 Extension。
 *
 * <p>Campus 未启用时无 Bean；调用方须 {@code ObjectProvider} 可选注入。</p>
 */
public interface PeisCampusStaffArchiveExtension {

    /**
     * 按工号幂等 upsert 教职工，并绑定患者（团检导入专用编排入口）。
     */
    PeisCampusStaffUpsertResult upsertStaffForRosterImport(PeisCampusStaffUpsertCommand command);
}
