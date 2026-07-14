/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.sequence;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.DateUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务序号服务：基于数据库记录每个实体的序号，替代 Redis 序号。
 *
 * <p>支持多租户：当存在当前租户上下文时，序号按租户隔离（每个租户独立递增）；宿主用户或
 * 无租户上下文时使用全局序号。内部通过将 key 解析为 {@code tenantId:key} 实现，无需改表结构。
 *
 * @author Whale Framework
 */
@Service
public class BusinessSequenceService {

    private static final Logger log = LoggerFactory.getLogger(BusinessSequenceService.class);

    private static final String FORMAT_SEQ_PREFIX = "%s%0";
    private static final String FORMAT_SEQ_SUFFIX = "d";
    private static final String FORMAT_SEQ_PREFIX_DAY = "%s%s%0";
    private static final int DEFAULT_SUFFIX_LENGTH = 8;
    private static final int DEFAULT_SUFFIX_LENGTH_DAY = 4;

    private final JdbcTemplate jdbcTemplate;

    public BusinessSequenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 解析带租户的序号键：有当前租户时返回 {@code tenantId:key}，否则返回原 key。
     */
    private String resolveKey(String key) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        return (tenantId != null ? tenantId + ":" : "") + key;
    }

    /**
     * 原子递增并返回新值（数据库实现）。key 会按当前租户自动隔离。
     *
     * @param key   序号键（业务前缀或 prefix:date，租户前缀由框架自动加上）
     * @param delta 递增量
     * @return 递增后的当前值
     */

    public long nextValue(String key, long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("递增量必须大于 0");
        }
        String effectiveKey = resolveKey(key);
        String sql =
                "INSERT INTO whale_sequence (seq_key, current_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (seq_key) DO UPDATE SET current_value = whale_sequence.current_value + EXCLUDED.current_value, updated_at = CURRENT_TIMESTAMP "
                        + "RETURNING current_value";
        Long result =
                jdbcTemplate.execute(
                        (Connection conn) -> {
                            PreparedStatement ps = conn.prepareStatement(sql);
                            ps.setString(1, effectiveKey);
                            ps.setLong(2, delta);
                            ps.execute();
                            ResultSet rs = ps.getResultSet();
                            if (rs != null && rs.next()) {
                                return rs.getLong(1);
                            }
                            throw new BusinessException("SEQUENCE_ERROR", "序号生成失败");
                        });
        if (result != null && result >= Integer.MAX_VALUE) {
            log.error("序号已达上限: key={}", effectiveKey);
            throw new BusinessException("SEQUENCE_ERROR", "序号已达上限");
        }
        return result != null ? result : 0L;
    }

    /**
     * 获取下一个序号（单个）。
     *
     * @param key 序号键
     * @return 下一个序号值
     */
    public int getSeqNo(String key) {
        return (int) nextValue(key, 1);
    }

    /**
     * 获取下一段序号（批量），返回 [start, start+1, ..., end]。
     *
     * @param key   序号键
     * @param count 数量
     * @return 序号列表
     */
    public List<Integer> getSeqNo(String key, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        long seqEnd = nextValue(key, count);
        List<Integer> list = new ArrayList<>(count);
        long start = seqEnd - count + 1;
        for (long i = start; i <= seqEnd; i++) {
            list.add((int) i);
        }
        return list;
    }

    /**
     * 获取带前缀的编号（如 ORDER00000001）。
     *
     * @param prefix 编号前缀
     * @return 编号
     */
    public String getSeq(String prefix) {
        return getSeq(prefix, DEFAULT_SUFFIX_LENGTH);
    }

    /**
     * 获取带前缀的编号，指定序号位数。
     *
     * @param prefix    编号前缀
     * @param seqLength 序号位数（去前缀后）
     * @return 编号
     */
    public String getSeq(String prefix, int seqLength) {
        int seq = getSeqNo(prefix);
        return String.format(FORMAT_SEQ_PREFIX + seqLength + FORMAT_SEQ_SUFFIX, prefix, seq);
    }

    /**
     * 批量获取带前缀的编号。
     *
     * @param prefix    编号前缀
     * @param seqLength 序号位数
     * @param count     数量
     * @return 编号列表
     */
    public List<String> getSeq(String prefix, int seqLength, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        List<Integer> seqNos = getSeqNo(prefix, count);
        List<String> result = new ArrayList<>(seqNos.size());
        for (Integer seq : seqNos) {
            result.add(
                    String.format(FORMAT_SEQ_PREFIX + seqLength + FORMAT_SEQ_SUFFIX, prefix, seq));
        }
        return result;
    }

    /**
     * 按天重置的序号：当天第一个编号（如 ORDER202501300001）。
     *
     * @param prefix 编号前缀
     * @return 编号
     */
    public String getSeqByDay(String prefix) {
        return getSeqByDay(prefix, DEFAULT_SUFFIX_LENGTH_DAY);
    }

    /**
     * 按天重置的序号，指定序号位数。
     *
     * @param prefix    编号前缀
     * @param seqLength 序号位数
     * @return 编号
     */
    public String getSeqByDay(String prefix, int seqLength) {
        String today = DateUtils.today();
        int seq = getSeqNo(prefix + ":" + today);
        return String.format(
                FORMAT_SEQ_PREFIX_DAY + seqLength + FORMAT_SEQ_SUFFIX, prefix, today, seq);
    }

    /**
     * 按天批量获取编号。
     *
     * @param prefix    编号前缀
     * @param seqLength 序号位数
     * @param count     数量
     * @return 编号列表
     */
    public List<String> getSeqByDay(String prefix, int seqLength, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        String today = DateUtils.today();
        List<Integer> seqNos = getSeqNo(prefix + ":" + today, count);
        List<String> result = new ArrayList<>(seqNos.size());
        for (Integer seq : seqNos) {
            result.add(
                    String.format(
                            FORMAT_SEQ_PREFIX_DAY + seqLength + FORMAT_SEQ_SUFFIX,
                            prefix,
                            today,
                            seq));
        }
        return result;
    }

    /**
     * 按天获取下一个序号（单个）。
     *
     * @param prefix 编号前缀
     * @return 序号
     */
    public int getSeqNoByDay(String prefix) {
        return getSeqNo(prefix + ":" + DateUtils.today());
    }

    /**
     * 按天批量获取序号。
     *
     * @param prefix 编号前缀
     * @param count  数量
     * @return 序号列表
     */
    public List<Integer> getSeqNoByDay(String prefix, int count) {
        return getSeqNo(prefix + ":" + DateUtils.today(), count);
    }

    /**
     * 格式化字符串：基串 + 数字部分（固定位数），如 base.0001。
     *
     * @param baseStr   基础字符串
     * @param num       数字部分
     * @param numDigits 数字位数
     * @return 格式化后的字符串
     */
    public static String formatString(String baseStr, long num, int numDigits) {
        String formatPattern = "%s.%0" + numDigits + "d";
        return String.format(formatPattern, baseStr, num);
    }
}
