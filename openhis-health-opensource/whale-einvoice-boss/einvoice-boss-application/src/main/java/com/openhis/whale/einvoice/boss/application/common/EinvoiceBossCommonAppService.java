package com.openhis.whale.einvoice.boss.application.common;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.application.dto.IssueCommonResponse;
import com.openhis.whale.einvoice.boss.application.issue.BossIssueWorkerAppService;
import com.openhis.whale.einvoice.boss.application.request.IssueCommonRequest;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.contracts.enums.AmountStatus;
import com.openhis.whale.einvoice.boss.contracts.enums.CommonBusType;
import com.openhis.whale.einvoice.boss.contracts.enums.IssueKind;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskTriggerSource;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import com.openhis.whale.einvoice.boss.domain.service.BossInvoiceAmountValidator;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.task.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 医疗通用票开具编排（P3 · 不依赖 health adm_invoice）。
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossCommonAppService {

    private static final long DEFAULT_TENANT_ID = 1L;
    private static final DateTimeFormatter BUS_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final EinvBossIssueTaskRepository taskRepository;
    private final BossInvoiceAmountValidator amountValidator;
    private final BossIssueWorkerAppService issueWorker;
    private final EinvoiceBossProperties properties;

    /**
     * 开具医疗通用电子票据。
     *
     * @param request 开具请求
     * @return 开具结果
     */
    public IssueCommonResponse issueCommon(IssueCommonRequest request) {
        validateRequest(request);
        Long tenantId = resolveTenantId();
        String busType = resolveCommonBusType(request.getCommonBusType());
        BigDecimal totalAmt = request.getTotalAmt().setScale(2, RoundingMode.HALF_UP);
        List<BigDecimal> lineAmounts = request.getLines().stream()
                .map(line -> line.getAmount().setScale(2, RoundingMode.HALF_UP))
                .collect(Collectors.toList());
        amountValidator.validate(totalAmt, lineAmounts);

        EinvBossDocument document = new EinvBossDocument();
        document.setTenantId(tenantId);
        document.setIssueKind(IssueKind.COM.getCode());
        document.setBusNo(generateBusNo());
        document.setBusType(busType);
        document.setTotalAmt(totalAmt);
        document.setPaymentAmt(totalAmt);
        document.setAmountStatus(AmountStatus.MATCH.getCode());
        document.setPlatformProfile(properties.getPlatform().getProfile());
        document.setPayerName(request.getPayer().trim());
        document.setPayerPhone(StringUtils.trimToNull(request.getPayerPhone()));
        document.setPayerIdCard(StringUtils.trimToNull(request.getPayerIdCard()));
        document.setPayerType(StringUtils.defaultIfBlank(request.getPayerType(), "1"));
        document.setPayerSex(StringUtils.defaultIfBlank(request.getPayerSex(), "男"));
        document.setPayerAge(StringUtils.defaultIfBlank(request.getPayerAge(), "0"));
        document.setRemark(StringUtils.trimToNull(request.getRemark()));
        document.setOperatorName(StringUtils.trimToNull(request.getOperatorName()));
        document.setIsDeleted(false);
        document = documentRepository.save(document);

        document.setInvoiceId(document.getId());
        document.setPaymentId(document.getId());
        document = documentRepository.save(document);

        List<EinvBossLine> lines = buildLines(document, request);
        lineRepository.saveAll(lines);

        EinvBossIssueTask task = new EinvBossIssueTask();
        task.setTenantId(tenantId);
        task.setInvoiceId(document.getInvoiceId());
        task.setDocumentId(document.getId());
        task.setStatus(TaskStatus.PENDING.getCode());
        task.setAttemptCount(0);
        task.setTriggerSource(TaskTriggerSource.MANUAL.getCode());
        task.setIsDeleted(false);
        taskRepository.save(task);

        IssueCommonResponse response = new IssueCommonResponse();
        response.setDocumentId(document.getId());
        response.setBusNo(document.getBusNo());
        try {
            BossInvoiceIssueResult result = issueWorker.processByInvoiceId(document.getInvoiceId());
            response.setSuccess(true);
            response.setBillNo(result.getBillNo());
            response.setBillBatchCode(result.getBatchCode());
            response.setPictureUrl(result.getPictureUrl());
            response.setPictureNetUrl(result.getPictureNetUrl());
        } catch (Exception ex) {
            response.setSuccess(false);
            response.setMessage(ex.getMessage());
        }
        return response;
    }

    private void validateRequest(IssueCommonRequest request) {
        if (request == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "请求不能为空");
        }
        if (!StringUtils.isNotBlank(request.getPayer())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "交款人不能为空");
        }
        if (request.getTotalAmt() == null || request.getTotalAmt().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, "开票总额必须大于 0");
        }
        if (CollectionUtils.isEmpty(request.getLines())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, "收费明细不能为空");
        }
        for (IssueCommonRequest.CommonLineRequest line : request.getLines()) {
            if (!StringUtils.isNotBlank(line.getChargeCode()) || !StringUtils.isNotBlank(line.getChargeName())) {
                throw new BusinessException(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, "明细编码与名称不能为空");
            }
            if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, "明细金额必须大于 0");
            }
        }
        resolveCommonBusType(request.getCommonBusType());
    }

    private List<EinvBossLine> buildLines(EinvBossDocument document, IssueCommonRequest request) {
        List<EinvBossLine> lines = new ArrayList<>();
        int lineNo = 0;
        for (IssueCommonRequest.CommonLineRequest item : request.getLines()) {
            lineNo = lineNo + 1;
            EinvBossLine line = new EinvBossLine();
            line.setTenantId(document.getTenantId());
            line.setDocumentId(document.getId());
            line.setLineType("COM");
            line.setLineNo(lineNo);
            line.setChargeCode(item.getChargeCode().trim().toUpperCase(Locale.ROOT));
            line.setChargeName(item.getChargeName().trim());
            BigDecimal amount = item.getAmount().setScale(2, RoundingMode.HALF_UP);
            line.setStdAmount(amount);
            line.setAmount(amount);
            line.setQuantity(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE);
            line.setUnit(StringUtils.defaultIfBlank(item.getUnit(), "次"));
            line.setIsDeleted(false);
            line.setCreatedAt(LocalDateTime.now());
            lines.add(line);
        }
        return lines;
    }

    private String resolveCommonBusType(String raw) {
        if (!StringUtils.isNotBlank(raw)) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "通用票类型不能为空");
        }
        String code = raw.trim();
        for (CommonBusType type : CommonBusType.values()) {
            if (type.getCode().equals(code)) {
                return type.getCode();
            }
        }
        throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "不支持的通用票类型: " + raw);
    }

    private String generateBusNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "COM" + BUS_NO_TIME.format(LocalDateTime.now()) + suffix;
    }

    private Long resolveTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            return DEFAULT_TENANT_ID;
        }
        return tenantId;
    }
}
