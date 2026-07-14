/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.workflow;

import com.openhis.chs.bridge.inventory.support.InvPlatformSupport;
import com.openhis.chs.bridge.inventory.support.InvPlatformSupport.InvContractContext;
import com.openhis.chs.domain.inventory.access.InventoryInitializeAccess;
import com.openhis.chs.domain.inventory.access.InventoryUploadAccess;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.InitializeInventoryCommand;
import com.openhis.chs.domain.inventory.command.InitializeInventoryPairCommand;
import com.openhis.chs.domain.inventory.command.InvUpload3501Command;
import com.openhis.chs.domain.inventory.command.InvUpload3502Command;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.result.InitializeInventoryLineResult;
import com.openhis.chs.domain.inventory.result.InitializeInventoryResult;
import com.openhis.chs.domain.inventory.result.InvUpload3501Result;
import com.openhis.chs.domain.inventory.result.InvUpload3502Result;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3501+3502】批初始化 bridge 编排实现（P14）。
 *
 * <p><b>职责：</b>逐对先 3501 盘存再 3502 变更上报，依赖 {@link com.openhis.chs.domain.inventory.access.InventoryInitializeFillAccess} 提供的配对行。</p>
 * <p><b>实现端口：</b>{@link com.openhis.chs.domain.inventory.access.InventoryInitializeAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceInventoryAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P14</p>
 */
@Component
@RequiredArgsConstructor
public class InventoryInitializeWorkflow implements InventoryInitializeAccess {

    private final InvPlatformSupport invPlatformSupport;
    private final InventoryUploadAccess uploadWorkflow;

    public InitializeInventoryResult initialize(InitializeInventoryCommand command) {
        assertCommand(command);
        InvContractContext ctx = invPlatformSupport.resolveContract(command.getContractBusNo(), command.getContractId());

        InitializeInventoryResult result = new InitializeInventoryResult();
        if (command.getPairs() == null || command.getPairs().isEmpty()) {
            return result;
        }

        for (InitializeInventoryPairCommand pair : command.getPairs()) {
            if (pair == null || pair.getStocktake3501() == null || pair.getInvChange3502() == null) {
                continue;
            }
            result.setAttemptedPairs(result.getAttemptedPairs() + 1);
            InitializeInventoryLineResult lineResult = new InitializeInventoryLineResult();
            InvUploadLineCommand line3501 = pair.getStocktake3501();
            lineResult.setInventoryItemId(line3501.getSourceLineId());
            lineResult.setFixmedinsBchno(line3501.getFixmedinsBchno());
            lineResult.setMedListCodg(line3501.getMedListCodg());

            InvUpload3501Command single3501 = new InvUpload3501Command()
                .setContractBusNo(ctx.busNo())
                .setContractId(ctx.contract().id())
                .setSourceId(command.getLocationId())
                .setRemark(command.getRemark())
                .setSourceType(ChsInventoryConstants.SOURCE_TYPE_INIT)
                .setLines(java.util.List.of(line3501));
            InvUpload3501Result r3501 = firstOrNull(uploadWorkflow.uploadStocktake3501(single3501));
            if (r3501 == null) {
                continue;
            }
            lineResult.setUpload3501Id(r3501.getUploadId());
            lineResult.setUpload3501Status(r3501.getUploadStatus());

            if (ChsInventoryConstants.UPLOAD_STATUS_SKIPPED_NO_YB_NO.equals(r3501.getUploadStatus())) {
                result.setSkippedNoYbNo(result.getSkippedNoYbNo() + 1);
                lineResult.setPlatformMsg(r3501.getPlatformMsg());
                result.getLines().add(lineResult);
                continue;
            }
            if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(r3501.getUploadStatus())) {
                result.setFailedPairs(result.getFailedPairs() + 1);
                lineResult.setPlatformMsg(r3501.getPlatformMsg());
                result.getLines().add(lineResult);
                continue;
            }

            InvUpload3502Command batch3502 = new InvUpload3502Command()
                .setContractBusNo(ctx.busNo())
                .setContractId(ctx.contract().id())
                .setSourceId(command.getLocationId())
                .setSourceType(ChsInventoryConstants.SOURCE_TYPE_INIT)
                .setLines(java.util.List.of(pair.getInvChange3502()));
            InvUpload3502Result r3502 = firstOrNull(uploadWorkflow.uploadInvChange3502(batch3502));
            if (r3502 != null) {
                lineResult.setUpload3502Id(r3502.getUploadId());
                lineResult.setUpload3502Status(r3502.getUploadStatus());
                if (ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(r3502.getUploadStatus())) {
                    result.setSucceededPairs(result.getSucceededPairs() + 1);
                } else {
                    result.setFailedPairs(result.getFailedPairs() + 1);
                    lineResult.setPlatformMsg(r3502.getPlatformMsg());
                }
            }
            result.getLines().add(lineResult);
        }
        return result;
    }

    private static void assertCommand(InitializeInventoryCommand command) {
        if (command == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "初始化参数不能为空");
        }
        if (command.getLocationId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "locationId 不能为空");
        }
    }

    private static <T> T firstOrNull(java.util.List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
}
