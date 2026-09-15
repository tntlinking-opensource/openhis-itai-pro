/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.loader;

/**
 * 按就诊解析患者姓名（bridge 从 HIS 主数据实现；对账明细展示用）。
 */
public interface WorkInjuryPatientNameLookup {

    /**
     * @param encounterId 就诊 id
     * @return 患者姓名；找不到时 null
     */
    String findNameByEncounterId(Long encounterId);
}
