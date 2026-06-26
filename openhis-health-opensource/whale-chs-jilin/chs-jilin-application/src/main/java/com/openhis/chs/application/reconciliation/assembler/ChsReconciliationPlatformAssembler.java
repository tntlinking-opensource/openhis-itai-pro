/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.reconciliation.assembler;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.domain.reconciliation.dto.request.ApplyJilinClear3203ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearOrg3206ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearStatus3205ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDaily3201Request;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDetail3202Request;
import com.openhis.chs.domain.reconciliation.dto.request.QueryCrossProvinceRecon3209ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.RevokeJilinClear3204ARequest;

/**
 * 吉林对账清算平台报文 input.data 组装。
 *
 * <p>由 {@link com.openhis.chs.application.reconciliation.ChsReconciliationAppService} 调用。</p>
 */
public final class ChsReconciliationPlatformAssembler {

    private ChsReconciliationPlatformAssembler() {
    }

    /** 【3206A】清算机构分页查询。 */
    public static JSONObject assemble3206(QueryClearOrg3206ARequest request, ContractConfig config) {
        JSONObject data = new JSONObject();
        data.put("fixmedins_code", config.getFixmedinsCode());
        int pageNum;
        if (request.getPageNum() != null && request.getPageNum() > 0) {
            pageNum = request.getPageNum();
        } else {
            pageNum = 1;
        }
        data.put("page_num", pageNum);
        int pageSize;
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        } else {
            pageSize = 20;
        }
        data.put("page_size", Math.min(pageSize, 100));
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 【3203A】申请吉林清算。 */
    public static JSONObject assemble3203(ApplyJilinClear3203ARequest request) {
        JSONObject data = new JSONObject();
        data.put("clr_type", request.getClrType());
        data.put("medfee_sumamt", request.getMedfeeSumamt());
        data.put("med_sumfee", request.getMedSumfee());
        data.put("fund_appy_sum", request.getFundAppySum());
        data.put("cash_payamt", request.getCashPayamt());
        data.put("acct_pay", request.getAcctPay());
        data.put("begndate", request.getBegndate());
        data.put("enddate", request.getEnddate());
        data.put("clr_optins", request.getClrOptins());
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 【3205A】查询清算申请状态。 */
    public static JSONObject assemble3205(QueryClearStatus3205ARequest request) {
        JSONObject data = new JSONObject();
        data.put("clr_optins", request.getClrOptins());
        if (request.getClrStas() != null && !request.getClrStas().isBlank()) {
            data.put("clr_stas", request.getClrStas().trim());
        }
        if (request.getClrYm() != null && !request.getClrYm().isBlank()) {
            data.put("clr_ym", request.getClrYm().trim());
        }
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 【3204A】撤销吉林清算申请。 */
    public static JSONObject assemble3204(RevokeJilinClear3204ARequest request) {
        JSONObject data = new JSONObject();
        data.put("clr_appy_evt_id", request.getClrAppyEvtId());
        data.put("clr_optins", request.getClrOptins());
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 【3201】日对账：汇总金额 + 结算笔数与平台比对。 */
    public static JSONObject assemble3201(ReconDaily3201Request request) {
        JSONObject data = new JSONObject();
        data.put("insutype", request.getInsutype());
        data.put("clr_type", request.getClrType());
        data.put("setl_optins", request.getSetlOptins());
        data.put("stmt_begndate", request.getStmtBegndate());
        data.put("stmt_enddate", request.getStmtEnddate());
        data.put("medfee_sumamt", request.getMedfeeSumamt());
        data.put("fund_pay_sumamt", request.getFundPaySumamt());
        data.put("acct_pay", request.getAcctPay());
        data.put("fixmedins_setl_cnt", request.getFixmedinsSetlCnt());
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 【3202】明细对账：上传 file_qury_no 后平台返回差异文件。 */
    public static JSONObject assemble3202(ReconDetail3202Request request) {
        JSONObject data = new JSONObject();
        data.put("setl_optins", request.getSetlOptins());
        data.put("file_qury_no", request.getFileQuryNo());
        data.put("stmt_begndate", request.getStmtBegndate());
        data.put("stmt_enddate", request.getStmtEnddate());
        data.put("medfee_sumamt", request.getMedfeeSumamt());
        data.put("fund_pay_sumamt", request.getFundPaySumamt());
        data.put("cash_payamt", request.getCashPayamt());
        data.put("fixmedins_setl_cnt", request.getFixmedinsSetlCnt());
        data.put("clr_type", request.getClrType());
        data.put("refd_setl_flag", request.getRefdSetlFlag());
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }

    /** 【3209A】跨省三方对账失败记录查询。 */
    public static JSONObject assemble3209A(QueryCrossProvinceRecon3209ARequest request) {
        JSONObject data = new JSONObject();
        data.put("clr_type", request.getClrType());
        data.put("begndate", request.getBegndate());
        data.put("enddate", request.getEnddate());
        data.put("clr_optins", request.getClrOptins());
        JSONObject input = new JSONObject();
        input.put("data", data);
        return input;
    }
}
