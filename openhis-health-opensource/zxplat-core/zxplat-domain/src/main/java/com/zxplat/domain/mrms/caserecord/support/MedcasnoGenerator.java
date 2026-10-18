/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.support;

import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.entity.MedcasnoSequence;
import com.zxplat.domain.mrms.caserecord.repository.MedcasnoSequenceRepository;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.MrmsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 病案号发号器：{@code CR} + 两位年 + 两位月 + 四位年流水（如 {@code CR26080001}）。
 *
 * <p>后四位按<strong>自然年</strong>连续递增、跨月不重置；换年只看当年 yy（如 27）的后四位最大值，
 * 与上年（如 26 已到 9999）无关，新年从 {@code 0001} 起。
 * 对齐主档：先按号中两位年过滤，再按后四位倒序 {@code LIMIT 1}；
 * 序列表按 {@code year_value=yyyy} 分行锁，跨年各算各的。</p>
 */
@Service
@RequiredArgsConstructor
public class MedcasnoGenerator {

    /** 新规则病案号固定长度：{@code CR}(2) + yy(2) + MM(2) + seq(4)。 */
    private static final int MEDCASNO_LENGTH = 10;

    /** 后四位流水上限（含）。 */
    private static final int MAX_YEAR_SEQ = 9999;

    /** Criteria：前缀 {@code CR} 起点（1-based）。 */
    private static final int PREFIX_SUBSTRING_START = 1;

    /** Criteria：前缀长度。 */
    private static final int PREFIX_SUBSTRING_LEN = 2;

    /** Criteria：两位年起点（1-based，紧接 {@code CR}）。 */
    private static final int YEAR_SUBSTRING_START = 3;

    /** Criteria：两位年长度。 */
    private static final int YEAR_SUBSTRING_LEN = 2;

    /** Criteria：后四位流水起点（1-based）。 */
    private static final int SEQ_SUBSTRING_START = 7;

    /** Criteria：后四位流水长度。 */
    private static final int SEQ_SUBSTRING_LEN = 4;

    private final MedcasnoSequenceRepository medcasnoSequenceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 分配租户内唯一病案号（仅对齐<strong>当年</strong>流水 +1，月份取发号当日）。
     *
     * @param tenantId 租户主键（须非空）
     * @return 病案号，如 {@code CR27010001}（新年不接上年 9999）
     */
    @Transactional(rollbackFor = Exception.class)
    public String nextMedcasno(Long tenantId) {
        // Gate：租户空则拒绝发号，避免无隔离上下文撞号
        if (tenantId == null) {
            throw new BusinessException(MrmsErrorCodes.MEDCASNO_GENERATE_FAILED, "租户不能为空");
        }
        LocalDate today = LocalDate.now();
        int yearValue = today.getYear();
        int yearTwoDigits = yearValue % 100;
        int monthValue = today.getMonthValue();
        // Pre：锁「本自然年」序列行；主档也只取「本 yy」后四位 max（不看上年）
        MedcasnoSequence sequence = this.lockOrCreateYearSequence(tenantId, yearValue);
        int occupiedMax = this.resolveMaxSeqForYear(tenantId, yearTwoDigits);
        int baseline = sequence.getLastSeq();
        // 脏数据防护：历史序列表若残留非法末位，回落主档最大值
        if (baseline < 0 || baseline > MAX_YEAR_SEQ) {
            baseline = 0;
        }
        if (occupiedMax > baseline) {
            baseline = occupiedMax;
        }
        int nextSeq = baseline + 1;
        // Gate：四位流水耗尽则拒绝，避免溢出位数
        if (nextSeq > MAX_YEAR_SEQ) {
            throw new BusinessException(
                    MrmsErrorCodes.MEDCASNO_GENERATE_FAILED,
                    "本年度病案号流水已用尽");
        }
        // Cmd：回写年度末位，保证同事务内并发串行
        sequence.setLastSeq(nextSeq);
        sequence.setUpdatedAt(LocalDateTime.now());
        medcasnoSequenceRepository.save(sequence);
        return this.formatMedcasno(yearTwoDigits, monthValue, nextSeq);
    }

    /**
     * 悲观锁加载或创建租户+自然年序列行（按 yyyy 分行，跨年互不影响）。
     *
     * @param tenantId  租户
     * @param yearValue 自然年 yyyy
     * @return 已加锁的序列行
     */
    private MedcasnoSequence lockOrCreateYearSequence(Long tenantId, int yearValue) {
        // Pre：优先悲观锁已有行
        var existing = medcasnoSequenceRepository
                .findByTenantIdAndYearValueAndIsDeletedFalse(tenantId, yearValue);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Cmd：首发当年号时建序列行；唯一键冲突则回读加锁
        MedcasnoSequence created = new MedcasnoSequence();
        created.setTenantId(tenantId);
        created.setYearValue(yearValue);
        created.setLastSeq(0);
        created.setCreatedAt(LocalDateTime.now());
        created.setIsDeleted(false);
        try {
            medcasnoSequenceRepository.saveAndFlush(created);
        } catch (RuntimeException ex) {
            // 并发首插撞唯一键：回读加锁即可
            return medcasnoSequenceRepository
                    .findByTenantIdAndYearValueAndIsDeletedFalse(tenantId, yearValue)
                    .orElseThrow(() -> new BusinessException(
                            MrmsErrorCodes.MEDCASNO_GENERATE_FAILED,
                            "病案号年度序列初始化失败"));
        }
        return medcasnoSequenceRepository
                .findByTenantIdAndYearValueAndIsDeletedFalse(tenantId, yearValue)
                .orElse(created);
    }

    /**
     * 只取本租户、号中两位年={@code yearTwoDigits} 的新规则号，按后四位倒序 {@code LIMIT 1}。
     *
     * <p>例：今年 27 → 只看 {@code CR27??????}；上年 {@code CR26999999} 不参与。
     * 不可整串 {@code medcasno} 倒序：同年内月份会干扰后四位比较。</p>
     *
     * @param tenantId       租户
     * @param yearTwoDigits  两位年（如 27）
     * @return 该年后四位最大流水；无记录为 0（新年从 0001 起）
     */
    private int resolveMaxSeqForYear(Long tenantId, int yearTwoDigits) {
        String yy = this.padTwoDigits(yearTwoDigits);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<CaseRecord> root = cq.from(CaseRecord.class);
        cq.select(root.get("medcasno"));
        // 显式拎出号中两位年 = 当年 yy，排除上年号
        cq.where(
                cb.equal(root.get("tenantId"), tenantId),
                cb.equal(root.get("isDeleted"), false),
                cb.isNotNull(root.get("medcasno")),
                cb.equal(cb.length(root.get("medcasno")), MEDCASNO_LENGTH),
                cb.equal(
                        cb.substring(root.get("medcasno"), PREFIX_SUBSTRING_START, PREFIX_SUBSTRING_LEN),
                        AggregateTypeConstants.CASE_RECORD_MEDCASNO),
                cb.equal(
                        cb.substring(root.get("medcasno"), YEAR_SUBSTRING_START, YEAR_SUBSTRING_LEN),
                        yy));
        // 同年内按后四位倒序，只取 1 行
        cq.orderBy(cb.desc(cb.substring(root.get("medcasno"), SEQ_SUBSTRING_START, SEQ_SUBSTRING_LEN)));
        List<String> rows = entityManager.createQuery(cq).setMaxResults(1).getResultList();
        if (rows.isEmpty()) {
            return 0;
        }
        Integer parsed = this.parseYearSeq(rows.get(0), yearTwoDigits);
        if (parsed == null) {
            return 0;
        }
        return parsed;
    }

    /**
     * 解析新规则病案号后四位流水；非当年或非法格式返回 null。
     *
     * @param medcasno       病案号
     * @param yearTwoDigits  期望两位年
     * @return 流水或 null
     */
    private Integer parseYearSeq(String medcasno, int yearTwoDigits) {
        String no = trimToNull(medcasno);
        if (no == null || no.length() != MEDCASNO_LENGTH) {
            return null;
        }
        if (!no.startsWith(AggregateTypeConstants.CASE_RECORD_MEDCASNO)) {
            return null;
        }
        // 前缀后两位须为本年 yy
        String yyPart = no.substring(2, 4);
        String expectYy = this.padTwoDigits(yearTwoDigits);
        if (!expectYy.equals(yyPart)) {
            return null;
        }
        // 第 5～6 位为月，第 7～10 位为流水
        String seqPart = no.substring(6, 10);
        if (!this.isFourDigits(seqPart)) {
            return null;
        }
        return this.digitsToInt(seqPart);
    }

    /**
     * 组装病案号 {@code CR} + yy + MM + ####。
     *
     * @param yearTwoDigits 两位年
     * @param monthValue    月 1～12
     * @param seq           年流水
     * @return 病案号
     */
    private String formatMedcasno(int yearTwoDigits, int monthValue, int seq) {
        return AggregateTypeConstants.CASE_RECORD_MEDCASNO
                + this.padTwoDigits(yearTwoDigits)
                + this.padTwoDigits(monthValue)
                + this.padFourDigits(seq);
    }

    /**
     * 两位数字左补零。
     *
     * @param value 数值
     * @return 两位字符串
     */
    private String padTwoDigits(int value) {
        if (value < 10) {
            return "0" + value;
        }
        return String.valueOf(value);
    }

    /**
     * 四位数字左补零。
     *
     * @param value 数值
     * @return 四位字符串
     */
    private String padFourDigits(int value) {
        if (value < 10) {
            return "000" + value;
        }
        if (value < 100) {
            return "00" + value;
        }
        if (value < 1000) {
            return "0" + value;
        }
        return String.valueOf(value);
    }

    /**
     * 判断是否恰好四位数字字符。
     *
     * @param text 文本
     * @return true 表示四位数字
     */
    private boolean isFourDigits(String text) {
        if (text == null || text.length() != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 四位数字字符串转 int（流水解析，非枚举身份）。
     *
     * @param digits 四位数字
     * @return 整值
     */
    private int digitsToInt(String digits) {
        int value = 0;
        for (int i = 0; i < digits.length(); i++) {
            value = value * 10 + (digits.charAt(i) - '0');
        }
        return value;
    }
}
