package com.openhis.whale.report.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打印事件日汇总聚合查询（原生 SQL 条件下推 DB，避免 Repository 接口 {@code @Query}）。
 */
@Repository
public class PrintEventLogSummaryDao {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 按日、操作人、目标与事件类型聚合打印成功/失败次数与份数。
     *
     * @param tenantId    租户
     * @param createdFrom 统计起始（含）
     * @param createdTo   统计结束（含）
     * @param eventType   可选事件类型过滤
     * @param targetCode  可选目标编码模糊
     * @param keyword     可选操作人/目标关键字
     * @return 聚合行 Object[]，列顺序与 PrintEventLogQueryService 映射一致
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> aggregateDailySummary(
            Long tenantId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            String eventType,
            String targetCode,
            String keyword) {
        String sql = """
                SELECT CAST(l.created_at AS date) AS stat_date,
                       l.operator_id,
                       MAX(l.operator_name) AS operator_name,
                       l.target_type,
                       l.target_code,
                       l.event_type,
                       SUM(CASE WHEN UPPER(l.status) = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN UPPER(l.status) = 'FAILED' THEN 1 ELSE 0 END) AS fail_count,
                       SUM(CASE WHEN UPPER(l.status) = 'SUCCESS' THEN COALESCE(l.copies, 0) ELSE 0 END) AS total_copies,
                       MAX(l.created_at) AS last_event_at
                FROM rpt_print_event_log l
                WHERE l.tenant_id = :tenantId
                  AND l.created_at >= :createdFrom
                  AND l.created_at <= :createdTo
                  AND (:eventType IS NULL OR UPPER(l.event_type) = UPPER(:eventType))
                  AND (:targetCode IS NULL OR LOWER(l.target_code) LIKE LOWER(CONCAT('%', :targetCode, '%')))
                  AND (:keyword IS NULL OR (
                        LOWER(l.operator_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                     OR LOWER(l.target_code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  ))
                GROUP BY CAST(l.created_at AS date), l.operator_id, l.target_type, l.target_code, l.event_type
                ORDER BY stat_date DESC, l.target_code ASC, l.event_type ASC
                """;
        return entityManager.createNativeQuery(sql)
                .setParameter("tenantId", tenantId)
                .setParameter("createdFrom", createdFrom)
                .setParameter("createdTo", createdTo)
                .setParameter("eventType", eventType)
                .setParameter("targetCode", targetCode)
                .setParameter("keyword", keyword)
                .getResultList();
    }
}
