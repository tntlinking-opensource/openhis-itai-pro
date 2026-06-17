/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.constants;

/**
 * 医保协同单编排常量。
 */
public interface InsuranceCoordinationConstants {

    /** 非法状态转移（INS-012）。 */
    String INS_012 = "INS-012";

    /** 进行中协同单冲突（INS-003）。 */
    String INS_003 = "INS-003";

    /** 住院结算异步模式。 */
    String SETTLEMENT_MODE_INSURANCE_ASYNC = "INSURANCE_ASYNC";

    /** 住院结算同步模式。 */
    String SETTLEMENT_MODE_SYNC = "SYNC";

    /** IP_SETTLE 平台步骤 infno。 */
    String IP_SETTLE_PLATFORM_INFNO = "2304";

    /** OP_SETTLE 平台步骤 infno。 */
    String OP_SETTLE_PLATFORM_INFNO = "2207";

    /** OP_SETTLE_REVERSE 平台步骤 infno。 */
    String OP_SETTLE_REVERSE_PLATFORM_INFNO = "2208";

    /** DISPENSE_UPLOAD 销售上传 infno。 */
    String DISPENSE_UPLOAD_SALE_INFNO = "3505";

    /** DISPENSE_UPLOAD 退货上传 infno。 */
    String DISPENSE_UPLOAD_RETURN_INFNO = "3506";

    /** DISPENSE_UPLOAD 快照：发药 3505。 */
    String DISPENSE_UPLOAD_KIND_SALE = "SALE3505";

    /** DISPENSE_UPLOAD 快照：退药 3506。 */
    String DISPENSE_UPLOAD_KIND_RETURN = "RETURN3506";

    /** 本地落账最大重试次数。 */
    int IP_SETTLE_LOCAL_MAX_RETRIES = 5;

    /** OP_SETTLE 本地落账最大重试次数。 */
    int OP_SETTLE_LOCAL_MAX_RETRIES = 5;

    /** 本地落账退避基数（秒）。 */
    int LOCAL_RETRY_BACKOFF_BASE_SECONDS = 30;
}
