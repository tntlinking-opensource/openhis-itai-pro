/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.writer;

/**
 * 离院建档桥接 Port（adapter → domain）。
 */
public interface CaseRecordBootstrapWriter {

    /**
     * 幂等为就诊创建病案主档与空首页。
     *
     * @param tenantId 租户
     * @param patientId 患者
     * @param encounterId 就诊
     * @param operatorId 操作人
     * @return 病案主档主键
     */
    Long createIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId);
}
