/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.medicalinfo.facade;

import com.openhis.chs.domain.medicalinfo.dto.request.QueryCum5206Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryDise5202Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryFee5204Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryMdtrt5201Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspDrug5205Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspFiling5301Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QuerySetl5203Request;
import com.openhis.chs.domain.medicalinfo.dto.response.MedicalInfoQueryResult;
import com.openhis.chs.domain.medicalinfo.dto.response.Setl5203Result;

/** 5201–5206 医保信息查询 facade。 */
public interface ChsMedicalInfoQueryOperations {

    MedicalInfoQueryResult queryMdtrt5201(QueryMdtrt5201Request request);

    MedicalInfoQueryResult queryDise5202(QueryDise5202Request request);

    Setl5203Result querySetl5203(QuerySetl5203Request request);

    MedicalInfoQueryResult queryFee5204(QueryFee5204Request request);

    MedicalInfoQueryResult queryOpspDrug5205(QueryOpspDrug5205Request request);

    MedicalInfoQueryResult queryCum5206(QueryCum5206Request request);

    MedicalInfoQueryResult queryOpspFiling5301(QueryOpspFiling5301Request request);
}
