package com.openhis.whale.report.application.printlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.report.application.printlog.response.LogEventResponse;
import com.openhis.whale.report.application.printlog.response.PrintDailySummaryItemDto;
import com.openhis.whale.report.application.printlog.response.PrintEventLogItemDto;
import com.openhis.whale.report.application.printlog.service.PrintEventLogQueryService;
import com.openhis.whale.report.application.printlog.request.LogEventRequest;
import com.openhis.whale.report.application.printlog.request.QueryPrintDailySummaryRequest;
import com.openhis.whale.report.application.printlog.request.QueryPrintEventLogRequest;
import com.openhis.whale.report.application.support.ReportOperatorResolver;
import com.openhis.whale.report.domain.entity.PrintEventLog;
import com.openhis.whale.report.domain.service.PrintEventLogManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * 打印日志 AppService → {@code POST /api/app/reportPrintLog/*}
 */
@Service
@Validated
public class ReportPrintLogAppService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PrintEventLogManager printEventLogManager;
    private final PrintEventLogQueryService printEventLogQueryService;
    private final TenantContext tenantContext;
    private final ReportOperatorResolver operatorResolver;

    public ReportPrintLogAppService(
            PrintEventLogManager printEventLogManager,
            PrintEventLogQueryService printEventLogQueryService,
            TenantContext tenantContext,
            ReportOperatorResolver operatorResolver) {
        this.printEventLogManager = printEventLogManager;
        this.printEventLogQueryService = printEventLogQueryService;
        this.tenantContext = tenantContext;
        this.operatorResolver = operatorResolver;
    }

    /**
     * 客户端上报打印/预览事件 → {@code POST /api/app/reportPrintLog/logEvent}
     *
     * @param request 事件载荷
     * @return 落库后的事件 id
     */
    public LogEventResponse logEvent(@Valid LogEventRequest request) {
        // Gate：必填字段与 status 枚举校验
        validateRequest(request);

        PrintEventLog log = new PrintEventLog();
        // Gate：租户上下文须存在，否则无法按租户隔离写入
        Long tenantId = tenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new ValidationException("当前租户未设置，无法写入打印日志");
        }
        log.setTenantId(tenantId);
        // Cmd：映射请求字段到实体（bizRef 存库仍为 JSON 文本）
        log.setEventType(request.getEventType().trim());
        log.setTargetType(request.getTargetType().trim());
        log.setTargetCode(request.getTargetCode().trim());
        log.setTemplateVersion(request.getTemplateVersion());
        log.setDatasetCode(trimToNull(request.getDatasetCode()));
        log.setBizRefJson(toJson(request.getBizRef()));
        log.setCopies(resolveCopies(request.getCopies()));
        log.setStatus(request.getStatus().trim());
        log.setErrorCode(trimToNull(request.getErrorCode()));
        log.setErrorMessage(trimToNull(request.getErrorMessage()));
        log.setContentHash(trimToNull(request.getContentHash()));
        log.setContentType(trimToNull(request.getContentType()));
        log.setPrinterName(trimToNull(request.getPrinterName()));
        log.setClientJobId(trimToNull(request.getClientJobId()));
        log.setWinDurationMs(request.getWinDurationMs());
        log.setRenderDurationMs(request.getRenderDurationMs());
        log.setParentEventId(request.getParentEventId());
        log.setSource(resolveSource(request.getSource()));

        // Pre：从会话解析操作人，匿名上报时允许为空
        Long operatorId = operatorResolver.findOperatorIdOrNull();
        if (operatorId != null) {
            log.setOperatorId(operatorId);
            log.setOperatorName(operatorResolver.findOperatorNameOrNull());
            log.setOrganizationId(operatorResolver.findOrganizationIdOrNull());
        }

        PrintEventLog saved = printEventLogManager.insert(log);
        return new LogEventResponse(saved.getId());
    }

    /**
     * 打印事件明细分页 → {@code POST /api/app/reportPrintLog/queryPage}
     *
     * @param request 查询条件
     * @return 分页明细
     */
    public PagedResult<PrintEventLogItemDto> queryPage(@Valid QueryPrintEventLogRequest request) {
        // Gate：查询须绑定租户
        Long tenantId = requireTenantId();
        return printEventLogQueryService.queryPage(tenantId, request);
    }

    /**
     * 打印日汇总（由明细聚合） → {@code POST /api/app/reportPrintLog/queryDailySummary}
     *
     * @param request 统计区间与过滤
     * @return 分页汇总行
     */
    public PagedResult<PrintDailySummaryItemDto> queryDailySummary(
            @Valid QueryPrintDailySummaryRequest request) {
        Long tenantId = requireTenantId();
        return printEventLogQueryService.queryDailySummary(tenantId, request);
    }

    private Long requireTenantId() {
        Long tenantId = tenantContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new ValidationException("当前租户未设置，无法查询打印日志");
        }
        return tenantId;
    }

    private static void validateRequest(LogEventRequest request) {
        String status = request.getStatus().trim();
        if (!"SUCCESS".equalsIgnoreCase(status) && !"FAILED".equalsIgnoreCase(status)) {
            throw new ValidationException("status 必须为 SUCCESS 或 FAILED");
        }
    }

    private static int resolveCopies(Integer copies) {
        if (copies == null) {
            return 0;
        }
        return copies;
    }

    private static String resolveSource(String source) {
        if (StringUtils.hasText(source)) {
            return source.trim();
        }
        return "WEB";
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ValidationException("bizRef 无法序列化为 JSON");
        }
    }
}
