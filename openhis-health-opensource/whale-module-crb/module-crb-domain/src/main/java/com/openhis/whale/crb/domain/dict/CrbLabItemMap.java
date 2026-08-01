/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict;

import com.openhis.whale.crb.domain.enums.Annex2Scope;
import com.openhis.whale.crb.domain.enums.LabResultShape;
import com.openhis.whale.crb.domain.support.CrbTenantRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 检验项目国家码对照（crb_lab_item_map）。
 */
@Entity
@Table(name = "crb_lab_item_map")
@Getter
@Setter
public class CrbLabItemMap extends CrbTenantRowEntity {

    /** 院内检验项目业务码。 */
    @Column(name = "item_bus_no", nullable = false, length = 64)
    private String itemBusNo;

    /** 院内项目名称（展示）。 */
    @Column(name = "item_name", length = 256)
    private String itemName;

    /** 国家/标化检验项目码；空表示未对照。 */
    @Column(name = "national_item_code", length = 64)
    private String nationalItemCode;

    /** 结果形态 code：QUAL / QUANT。 */
    @Column(name = "result_shape", length = 16)
    private String resultShape;

    /** 附件二范围 code。 */
    @Column(name = "annex2_scope", length = 32)
    private String annex2Scope;

    /** 是否参与推送白名单。 */
    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = CrbDictConstants.DEFAULT_LAB_PUSH_ENABLED;

    /**
     * 校验结果形态 code 是否在白名单内。
     *
     * @param resultShape 结果形态 code
     */
    public static void assertResultShapeValid(String resultShape) {
        String shape = trimToNull(resultShape);
        // 空形态允许；非空则必须是已知枚举
        if (shape != null && !LabResultShape.isKnownCode(shape)) {
            throw new IllegalArgumentException(CrbDictConstants.MSG_INVALID_RESULT_SHAPE);
        }
    }

    /**
     * 按附件二范围规则解析最终 pushEnabled。
     *
     * @param annex2Scope 附件二范围 code
     * @param requestedPush 请求启用推送
     * @return 持久化应使用的 pushEnabled
     */
    public static boolean resolvePushEnabled(String annex2Scope, Boolean requestedPush) {
        boolean inScope = Annex2Scope.isInScope(trimToNull(annex2Scope));
        boolean wantPush;
        if (requestedPush == null) {
            wantPush = CrbDictConstants.DEFAULT_LAB_PUSH_ENABLED;
        } else {
            wantPush = requestedPush;
        }
        // 附件二范围外不可启用推送
        if (wantPush && !inScope) {
            return false;
        }
        return wantPush;
    }

    /**
     * 是否已填写国家检验项目码（非空且非空白）。
     *
     * @return 已对照则 true
     */
    public boolean isMapped() {
        return trimToNull(nationalItemCode) != null;
    }
}
