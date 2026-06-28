/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.qc.executor;

import com.openhis.health.domain.mrms.qc.model.QcRunResult;
import com.openhis.health.domain.share.enums.QcTrigger;

/**
 * 质控规则执行器（按 rule_code 硬编码分支）。
 */
public interface QcRuleExecutor {

    /**
     * 对指定病案执行已启用规则并 upsert 缺陷。
     *
     * @param caseRecordId 病案主档主键
     * @param trigger 触发来源
     * @param operatorId 操作人
     * @return 执行结果
     */
    QcRunResult execute(Long caseRecordId, QcTrigger trigger, Long operatorId);
}
