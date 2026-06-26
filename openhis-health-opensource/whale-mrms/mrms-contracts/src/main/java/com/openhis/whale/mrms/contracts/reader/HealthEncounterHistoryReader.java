/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.reader;

import com.openhis.whale.mrms.contracts.snapshot.TransferLineSnapshot;

import java.util.List;

/**
 * 就诊里程碑/转科历史读 Port。
 */
public interface HealthEncounterHistoryReader {

    /**
     * 解析离院方式字典码。
     *
     * @param encounterId 就诊主键
     * @return 离院方式码；未知时 null
     */
    String resolveDischargeWayCode(Long encounterId);

    /**
     * 转科路径列表（按时间升序）。
     *
     * @param encounterId 就诊主键
     * @return 转科行
     */
    List<TransferLineSnapshot> listTransfers(Long encounterId);
}
