package com.openhis.whale.report.application.printlog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.report.application.support.ReportPagedResultBuilder;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.report.application.printlog.response.PrintDailySummaryItemDto;
import com.openhis.whale.report.application.printlog.response.PrintEventLogItemDto;
import com.openhis.whale.report.application.printlog.request.QueryPrintDailySummaryRequest;
import com.openhis.whale.report.application.printlog.request.QueryPrintEventLogRequest;
import com.openhis.whale.report.domain.entity.PrintEventLog;
import com.openhis.whale.report.domain.repository.PrintEventLogRepository;
import com.openhis.whale.report.domain.repository.PrintEventLogSummaryDao;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 打印事件读侧：明细分页与日汇总聚合查询。
 */
@Service
public class PrintEventLogQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PrintEventLogRepository repository;
    private final PrintEventLogSummaryDao summaryDao;

    public PrintEventLogQueryService(
            PrintEventLogRepository repository,
            PrintEventLogSummaryDao summaryDao) {
        this.repository = repository;
        this.summaryDao = summaryDao;
    }

    /**
     * 打印事件明细分页。
     *
     * @param tenantId 租户
     * @param request  过滤与分页
     * @return 分页 DTO
     */
    public PagedResult<PrintEventLogItemDto> queryPage(Long tenantId, QueryPrintEventLogRequest request) {
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        Specification<PrintEventLog> spec = buildDetailSpec(tenantId, request);
        Page<PrintEventLog> page = repository.findAll(
                spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<PrintEventLogItemDto> items = page.getContent().stream().map(this::toItemDto).toList();
        return ReportPagedResultBuilder.fromPage(items, page);
    }

    /**
     * 按日聚合打印成功/失败统计。
     *
     * @param tenantId 租户
     * @param request  统计区间
     * @return 分页汇总
     */
    public PagedResult<PrintDailySummaryItemDto> queryDailySummary(
            Long tenantId,
            QueryPrintDailySummaryRequest request) {
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        LocalDate from = request.getStatDateFrom();
        LocalDate to = request.getStatDateTo();
        if (from == null && to == null) {
            to = LocalDate.now();
            from = to.minusDays(6);
        } else if (from == null) {
            from = to;
        } else if (to == null) {
            to = from;
        }
        if (from.isAfter(to)) {
            throw new ValidationException("statDateFrom 不能晚于 statDateTo");
        }
        LocalDateTime createdFrom = from.atStartOfDay();
        LocalDateTime createdTo = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<Object[]> rows = summaryDao.aggregateDailySummary(
                tenantId,
                createdFrom,
                createdTo,
                trimToNull(request.getEventType()),
                trimToNull(request.getTargetCode()),
                trimToNull(request.getKeyword()));

        List<PrintDailySummaryItemDto> allItems = rows.stream()
                .map(this::toSummaryDto)
                .toList();
        return ReportPagedResultBuilder.fromFullList(allItems, pageNo, pageSize);
    }

    private Specification<PrintEventLog> buildDetailSpec(Long tenantId, QueryPrintEventLogRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (StringUtils.hasText(request.getEventType())) {
                predicates.add(cb.equal(cb.upper(root.get("eventType")),
                        request.getEventType().trim().toUpperCase()));
            }
            if (StringUtils.hasText(request.getStatus())) {
                predicates.add(cb.equal(cb.upper(root.get("status")),
                        request.getStatus().trim().toUpperCase()));
            }
            if (StringUtils.hasText(request.getTargetType())) {
                predicates.add(cb.equal(cb.upper(root.get("targetType")),
                        request.getTargetType().trim().toUpperCase()));
            }
            if (StringUtils.hasText(request.getTargetCode())) {
                predicates.add(cb.like(cb.lower(root.get("targetCode")),
                        "%" + request.getTargetCode().trim().toLowerCase() + "%"));
            }
            if (request.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        request.getCreatedFrom().atStartOfDay()));
            }
            if (request.getCreatedTo() != null) {
                LocalDateTime end = request.getCreatedTo().plusDays(1).atStartOfDay().minusNanos(1);
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            if (StringUtils.hasText(request.getKeyword())) {
                String like = "%" + request.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("operatorName")), like),
                        cb.like(cb.lower(root.get("targetCode")), like),
                        cb.like(cb.lower(root.get("clientJobId")), like),
                        cb.like(cb.lower(root.get("errorMessage")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PrintEventLogItemDto toItemDto(PrintEventLog log) {
        PrintEventLogItemDto dto = new PrintEventLogItemDto();
        dto.setId(log.getId());
        dto.setEventType(log.getEventType());
        dto.setTargetType(log.getTargetType());
        dto.setTargetCode(log.getTargetCode());
        dto.setTemplateVersion(log.getTemplateVersion());
        dto.setDatasetCode(log.getDatasetCode());
        dto.setBizRef(parseBizRef(log.getBizRefJson()));
        dto.setOperatorId(log.getOperatorId());
        dto.setOperatorName(log.getOperatorName());
        dto.setOrganizationId(log.getOrganizationId());
        dto.setCopies(log.getCopies());
        dto.setStatus(log.getStatus());
        dto.setErrorCode(log.getErrorCode());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setContentHash(log.getContentHash());
        dto.setContentType(log.getContentType());
        dto.setPrinterName(log.getPrinterName());
        dto.setClientJobId(log.getClientJobId());
        dto.setWinDurationMs(log.getWinDurationMs());
        dto.setRenderDurationMs(log.getRenderDurationMs());
        dto.setSource(log.getSource());
        dto.setParentEventId(log.getParentEventId());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private static Map<String, Object> parseBizRef(String bizRefJson) {
        if (!StringUtils.hasText(bizRefJson)) {
            return null;
        }
        try {
            return JSON.readValue(bizRefJson.trim(), new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("_raw", bizRefJson);
        }
    }

    private PrintDailySummaryItemDto toSummaryDto(Object[] row) {
        PrintDailySummaryItemDto dto = new PrintDailySummaryItemDto();
        dto.setStatDate(toLocalDate(row[0]));
        dto.setOperatorId(toLongOrNull(row[1]));
        dto.setOperatorName(toStringOrNull(row[2]));
        dto.setTargetType(toStringOrNull(row[3]));
        dto.setTargetCode(toStringOrNull(row[4]));
        dto.setEventType(toStringOrNull(row[5]));
        dto.setSuccessCount(toIntOrZero(row[6]));
        dto.setFailCount(toIntOrZero(row[7]));
        dto.setTotalCopies(toIntOrZero(row[8]));
        dto.setLastEventAt(toLocalDateTime(row[9]));
        return dto;
    }

    private static Long toLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static int toIntOrZero(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString());
    }

    private static int normalizePageNo(Integer pageNo) {
        if (pageNo != null && pageNo > 0) {
            return pageNo;
        }
        return 1;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
