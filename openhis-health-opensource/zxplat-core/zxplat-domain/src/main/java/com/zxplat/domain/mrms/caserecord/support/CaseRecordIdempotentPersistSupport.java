/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.support;

import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.zxplat.domain.mrms.caserecord.repository.CaseRecordHomepageRepository;
import com.zxplat.domain.mrms.caserecord.repository.CaseRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 病案主档/首页并发建档写侧：独立事务 {@code saveAndFlush}，供唯一键冲突后回读。
 *
 * <p>PostgreSQL 在同事务内 INSERT 撞 {@code uk_mrms_case_encounter} /
 * {@code uk_mrms_homepage_case} 后会话进入 aborted；须用 {@link Propagation#REQUIRES_NEW}
 * 隔离插入。冲突时仅新建事务回滚，外层事务仍可查询胜出方已提交行。
 * 成功插入会先于外层提交（create-if-absent 幂等可接受）。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordIdempotentPersistSupport {

    private final CaseRecordRepository caseRecordRepository;
    private final CaseRecordHomepageRepository homepageRepository;

    /**
     * 独立事务插入病案主档并立即 flush（触发唯一索引）。
     *
     * @param record 已 {@code initNewRecord}、尚未持久化的主档
     * @return 持久化后的主档
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CaseRecord insertCaseRecordNew(CaseRecord record) {
        // Cmd：flush 使 uk_mrms_case_encounter 在本事务内立即校验并提交
        return caseRecordRepository.saveAndFlush(record);
    }

    /**
     * 独立事务插入病案首页并立即 flush（触发唯一索引）。
     *
     * @param homepage 已 {@code initEmpty}、尚未持久化的首页
     * @return 持久化后的首页
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CaseRecordHomepage insertHomepageNew(CaseRecordHomepage homepage) {
        // Cmd：flush 使 uk_mrms_homepage_case 在本事务内立即校验并提交
        return homepageRepository.saveAndFlush(homepage);
    }
}
