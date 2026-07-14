/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.share;

import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import com.openhis.chs.domain.share.enums.HiFeesetlType;
import com.openhis.chs.domain.share.enums.InvChgType;
import com.openhis.chs.domain.share.enums.InvDataType;
import com.openhis.chs.domain.share.enums.ListType;
import com.openhis.chs.domain.share.enums.MdtrtSetlType;
import com.openhis.chs.domain.share.enums.MidSetlFlag;
import com.openhis.chs.domain.share.enums.ProdGeayFlag;
import com.openhis.chs.domain.share.enums.PsnCertType;
import com.openhis.chs.domain.share.enums.TrdnFlag;
import com.openhis.chs.domain.share.enums.ValiFlag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * share 包医保枚举/常量合并后的<b>回归守卫</b>：确保旧散落类删除后取值未丢失。
 *
 * <p>旧类 → 新落点对照见 whale-doc {@code 代码生成通用规范/references/chs-platform-enum-constants.md}。</p>
 */
class ChsPlatformShareMigrationCompletenessTest {

    @Test
    void invChgTypeCoversTable311Codes101Through111() {
        Set<String> codes = Arrays.stream(InvChgType.values())
            .map(InvChgType::getCode)
            .collect(Collectors.toSet());
        assertThat(codes)
            .as("3502 inv_chg_type 须覆盖 101～111 全表")
            .containsExactlyInAnyOrder(
                "101", "102", "103", "104", "105", "106", "107", "108", "109", "110", "111");
    }

    @Test
    void platformFieldEnumsPreserveOldChsInventoryConstantsCodes() {
        assertThat(TrdnFlag.WHOLE.getCode()).isEqualTo("0");
        assertThat(TrdnFlag.SPLIT.getCode()).isEqualTo("1");
        assertThat(MdtrtSetlType.INSURANCE.getCode()).isEqualTo("1");
        assertThat(MdtrtSetlType.SELF_PAY.getCode()).isEqualTo("2");
        assertThat(HiFeesetlType.SELF.getCode()).isEqualTo("0");
        assertThat(HiFeesetlType.LOCAL.getCode()).isEqualTo("1");
        assertThat(HiFeesetlType.REMOTE.getCode()).isEqualTo("2");
        assertThat(PsnCertType.ID_CARD.getCode()).isEqualTo("01");
        assertThat(ProdGeayFlag.NO.getCode()).isEqualTo("0");
        assertThat(InvDataType.STOCKTAKE.getCode()).isEqualTo("1");
        assertThat(InvDataType.INV_CHANGE.getCode()).isEqualTo("2");
        assertThat(InvDataType.PURCHASE.getCode()).isEqualTo("3");
        assertThat(InvDataType.SALE.getCode()).isEqualTo("4");
    }

    @Test
    void chsInventoryInternalConstantsPreserved() {
        assertThat(ChsInventoryConstants.SOURCE_TYPE_DISPENSE).isEqualTo("DISPENSE");
        assertThat(ChsInventoryConstants.SOURCE_TYPE_DEPT_ISSUE).isEqualTo("DEPT_ISSUE");
        assertThat(ChsInventoryConstants.SOURCE_TYPE_DEPT_ISSUE_RETURN).isEqualTo("DEPT_ISSUE_RETURN");
        assertThat(ChsInventoryConstants.COMPARE_DIFF_MATCH).isEqualTo("MATCH");
        assertThat(ChsInventoryConstants.UPLOAD_STATUS_PENDING).isEqualTo("PENDING");
        assertThat(ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED).isEqualTo("TRACE_REQUIRED");
        assertThat(ChsInventoryConstants.PLATFORM_RET_RSLT_SUCCESS).isEqualTo("1");
        assertThat(ChsInventoryConstants.INITIALIZE_INVENTORY_DEFAULT_MAX_LINES).isEqualTo(200);
        assertThat(ChsInventoryConstants.LOCAL_UPLOAD_LIST_DEFAULT_LIMIT).isEqualTo(500);
        assertThat(ChsInventoryConstants.RETRYABLE_UPLOAD_STATUSES)
            .containsExactly(ChsInventoryConstants.UPLOAD_STATUS_FAILED, ChsInventoryConstants.UPLOAD_STATUS_PENDING);
    }

    @Test
    void listTypeAndValiFlagPreserveOldCatalogDefaults() {
        assertThat(ListType.WESTERN_DRUG.getCode()).isEqualTo("101");
        assertThat(ListType.HERB.getCode()).isEqualTo("102");
        assertThat(ListType.MEDICAL_SERVICE.getCode()).isEqualTo("201");
        assertThat(ListType.MEDICAL_CONSUMABLE.getCode()).isEqualTo("301");
        assertThat(ValiFlag.VALID.getCode()).isEqualTo("1");
    }

    @Test
    void midSetlFlagPreservesOldChsInpatientConstants() {
        assertThat(MidSetlFlag.YES.getCode()).isEqualTo("1");
        assertThat(MidSetlFlag.NO.getCode()).isEqualTo("0");
    }

    @Test
    void chsInfNoMergesGatewayAndSettlementInfNos() {
        assertThat(ChsPlatformCodes.INFCODE_SUCCESS).isEqualTo("0");
        assertThat(ChsInfNo.OUTPATIENT_SETTLE).isEqualTo("2207");
        assertThat(ChsInfNo.INPATIENT_SETTLE).isEqualTo("2304");
        assertThat(ChsInfNo.TRANSACTION_CORRECT).isEqualTo("2601");
        assertThat(ChsInfNo.INVENTORY_CHANGE).isEqualTo("3502");
        assertThat(ChsInfNo.INVENTORY_SALE).isEqualTo("3505");
        assertThat(ChsInfNo.DICT_QUERY).isEqualTo("1901");
        assertThat(ChsInfNo.CATALOG_QUERY).isEqualTo("1312");
        assertThat(ChsInfNo.DRUG_CATALOG_DOWNLOAD).isEqualTo("1301");
        assertThat(ChsInfNo.MEDCAS_HOMEPAGE).isEqualTo("4401");
    }

    @Test
    void chsInfNoHasDisplayNameForEveryInfnoConstant() throws Exception {
        List<String> infnos = readPublicStringConstants(ChsInfNo.class);
        assertThat(infnos).as("ChsInfNo 交易号数量不得少于合并前 gateway 版").hasSizeGreaterThanOrEqualTo(60);
        for (String infno : infnos) {
            String display = ChsInfNo.resolveDisplayName(infno);
            assertThat(display)
                .as("infno=%s 须有中文展示名", infno)
                .isNotBlank()
                .isNotEqualTo(infno);
        }
    }

    private static List<String> readPublicStringConstants(Class<?> type) throws Exception {
        List<String> values = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() != String.class) {
                continue;
            }
            Object value = field.get(null);
            if (value instanceof String stringValue) {
                values.add(stringValue);
            }
        }
        return values;
    }
}
