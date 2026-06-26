package com.openhis.whale.einvoice.boss.application.issue;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.application.dto.BatchCompensateResultDto;
import com.openhis.whale.einvoice.boss.application.request.BatchInvoiceIdsRequest;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 批量补开编排（P2），独立 Service 以保证事务代理生效。
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossBatchIssueAppService {

    private final EinvoiceBossIssueAppService issueAppService;

    /**
     * 批量重新入队。
     *
     * @param request 票据主键列表
     * @return 批量结果
     */
    public BatchCompensateResultDto batchCompensateRequeue(BatchInvoiceIdsRequest request) {
        BatchCompensateResultDto result = new BatchCompensateResultDto();
        if (request == null || request.getInvoiceIds() == null || request.getInvoiceIds().isEmpty()) {
            throw new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "请选择待补开票据");
        }
        for (Long invoiceId : request.getInvoiceIds()) {
            BatchCompensateResultDto.BatchCompensateItemDto item = new BatchCompensateResultDto.BatchCompensateItemDto();
            item.setInvoiceId(invoiceId);
            try {
                issueAppService.compensateRequeue(invoiceId);
                item.setSuccess(true);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                item.setSuccess(false);
                item.setMessage(ex.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }
            result.getItems().add(item);
        }
        return result;
    }

    /**
     * 批量立即开具。
     *
     * @param request 票据主键列表
     * @return 批量结果
     */
    public BatchCompensateResultDto batchIssueNow(BatchInvoiceIdsRequest request) {
        BatchCompensateResultDto result = new BatchCompensateResultDto();
        if (request == null || request.getInvoiceIds() == null || request.getInvoiceIds().isEmpty()) {
            throw new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "请选择待补开票据");
        }
        for (Long invoiceId : request.getInvoiceIds()) {
            BatchCompensateResultDto.BatchCompensateItemDto item = new BatchCompensateResultDto.BatchCompensateItemDto();
            item.setInvoiceId(invoiceId);
            try {
                BossInvoiceIssueResult issueResult = issueAppService.issueNow(invoiceId);
                item.setSuccess(true);
                if (issueResult != null) {
                    item.setBillNo(issueResult.getBillNo());
                }
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                item.setSuccess(false);
                item.setMessage(ex.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }
            result.getItems().add(item);
        }
        return result;
    }
}
