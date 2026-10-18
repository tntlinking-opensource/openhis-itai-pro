/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.reconciliation.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3201Input;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3202Input;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3203AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3204AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3205AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3206AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3209AInput;

import com.zxplat.plugin.chs.domain.reconciliation.command.ApplyJilinClear3203ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.command.QueryClearOrg3206ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.command.QueryClearStatus3205ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.command.QueryCrossProvinceRecon3209ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.command.ReconDaily3201Command;
import com.zxplat.plugin.chs.domain.reconciliation.command.ReconDetail3202Command;
import com.zxplat.plugin.chs.domain.reconciliation.command.RevokeJilinClear3204ACommand;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3201Input;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3202Input;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3203AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3204AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3205AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3206AInput;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.model.Recon3209AInput;

/** 吉林对账清算平台报文 input.data 组装 */
public final class ReconPlatformInputAssembler {

    private ReconPlatformInputAssembler() {
    }

    public static Recon3206AInput assemble3206(QueryClearOrg3206ACommand command, String fixmedinsCode) {
        int pageNum = command.getPageNum() != null && command.getPageNum() > 0 ? command.getPageNum() : 1;
        int pageSize = command.getPageSize() != null && command.getPageSize() > 0 ? command.getPageSize() : 20;
        Recon3206AInput.Recon3206AData data = new Recon3206AInput.Recon3206AData()
            .setFixmedinsCode(fixmedinsCode)
            .setPageNum(pageNum)
            .setPageSize(Math.min(pageSize, 100));
        Recon3206AInput input = new Recon3206AInput();
        input.setData(data);
        return input;
    }

    public static Recon3203AInput assemble3203(ApplyJilinClear3203ACommand command) {
        Recon3203AInput.Recon3203AData data = new Recon3203AInput.Recon3203AData()
            .setClrType(command.getClrType())
            .setMedfeeSumamt(command.getMedfeeSumamt())
            .setMedSumfee(command.getMedSumfee())
            .setFundAppySum(command.getFundAppySum())
            .setCashPayamt(command.getCashPayamt())
            .setAcctPay(command.getAcctPay())
            .setBegndate(command.getBegndate())
            .setEnddate(command.getEnddate())
            .setClrOptins(command.getClrOptins());
        Recon3203AInput input = new Recon3203AInput();
        input.setData(data);
        return input;
    }

    public static Recon3205AInput assemble3205(QueryClearStatus3205ACommand command) {
        Recon3205AInput.Recon3205AData data = new Recon3205AInput.Recon3205AData()
            .setClrOptins(command.getClrOptins());
        if (isNotEmpty(command.getClrStas())) {
            data.setClrStas(command.getClrStas().trim());
        }
        if (isNotEmpty(command.getClrYm())) {
            data.setClrYm(command.getClrYm().trim());
        }
        Recon3205AInput input = new Recon3205AInput();
        input.setData(data);
        return input;
    }

    public static Recon3204AInput assemble3204(RevokeJilinClear3204ACommand command) {
        Recon3204AInput.Recon3204AData data = new Recon3204AInput.Recon3204AData()
            .setClrAppyEvtId(command.getClrAppyEvtId())
            .setClrOptins(command.getClrOptins());
        Recon3204AInput input = new Recon3204AInput();
        input.setData(data);
        return input;
    }

    public static Recon3201Input assemble3201(ReconDaily3201Command command) {
        Recon3201Input.Recon3201Data data = new Recon3201Input.Recon3201Data()
            .setInsutype(command.getInsutype())
            .setClrType(command.getClrType())
            .setSetlOptins(command.getSetlOptins())
            .setStmtBegndate(command.getStmtBegndate())
            .setStmtEnddate(command.getStmtEnddate())
            .setMedfeeSumamt(command.getMedfeeSumamt())
            .setFundPaySumamt(command.getFundPaySumamt())
            .setAcctPay(command.getAcctPay())
            .setFixmedinsSetlCnt(command.getFixmedinsSetlCnt());
        Recon3201Input input = new Recon3201Input();
        input.setData(data);
        return input;
    }

    public static Recon3202Input assemble3202(ReconDetail3202Command command) {
        Recon3202Input.Recon3202Data data = new Recon3202Input.Recon3202Data()
            .setSetlOptins(command.getSetlOptins())
            .setFileQuryNo(command.getFileQuryNo())
            .setStmtBegndate(command.getStmtBegndate())
            .setStmtEnddate(command.getStmtEnddate())
            .setMedfeeSumamt(command.getMedfeeSumamt())
            .setFundPaySumamt(command.getFundPaySumamt())
            .setCashPayamt(command.getCashPayamt())
            .setFixmedinsSetlCnt(command.getFixmedinsSetlCnt())
            .setClrType(command.getClrType())
            .setRefdSetlFlag(command.getRefdSetlFlag());
        Recon3202Input input = new Recon3202Input();
        input.setData(data);
        return input;
    }

    public static Recon3209AInput assemble3209A(QueryCrossProvinceRecon3209ACommand command) {
        Recon3209AInput.Recon3209AData data = new Recon3209AInput.Recon3209AData()
            .setClrType(command.getClrType())
            .setBegndate(command.getBegndate())
            .setEnddate(command.getEnddate())
            .setClrOptins(command.getClrOptins());
        Recon3209AInput input = new Recon3209AInput();
        input.setData(data);
        return input;
    }
}
