/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.access;

import com.openhis.chs.domain.platformdict.command.UpsertPlatformDictRowCommand;

import java.util.List;

/**
 * 【1901】单页字典拉取出站端口。
 *
 * <p><b>职责：</b>拉取指定父节点下一页 1901 字典数据，解析为可入库命令。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.foundation.platformdict.invoker.Dict1901PageInvoker}</p>
 * <p><b>调用方：</b>{@code Dict1901SyncWorkflow}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P15</p>
 */
public interface Dict1901PageInvoker {

    /**
     * 拉取指定父节点下的一页字典数据。
     *
     * @param contractBusNo 契约业务编号
     * @param dictType 字典类型编码
     * @param queryAdmdvs 查询统筹区划
     * @param parentValue 父节点值；根节点传空
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页行数
     * @param operatorId 操作员 ID（bridge 内部构建网关调用上下文）
     * @param valiFlagKey 有效标志字段 key
     * @return 可入库的字典行命令列表
     */
    List<UpsertPlatformDictRowCommand> fetchPage(
            String contractBusNo,
            String dictType,
            String queryAdmdvs,
            String parentValue,
            int pageNum,
            int pageSize,
            Long operatorId,
            String valiFlagKey);
}
