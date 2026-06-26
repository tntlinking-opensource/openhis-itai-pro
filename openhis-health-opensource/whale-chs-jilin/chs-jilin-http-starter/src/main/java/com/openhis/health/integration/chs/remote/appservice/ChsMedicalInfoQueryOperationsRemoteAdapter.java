package com.openhis.health.integration.chs.remote.appservice;

import com.openhis.chs.domain.medicalinfo.dto.request.QueryCum5206Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryDise5202Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryFee5204Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryMdtrt5201Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspDrug5205Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspFiling5301Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QuerySetl5203Request;
import com.openhis.chs.domain.medicalinfo.dto.response.MedicalInfoQueryResult;
import com.openhis.chs.domain.medicalinfo.dto.response.Setl5203Result;
import com.openhis.chs.domain.medicalinfo.facade.ChsMedicalInfoQueryOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsMedicalInfoQueryOperationsRemoteAdapter implements ChsMedicalInfoQueryOperations {

    private static final String SERVICE = "chsMedicalInfoQuery";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public MedicalInfoQueryResult queryMdtrt5201(QueryMdtrt5201Request request) {
        return remoteClient.post(SERVICE, "queryMdtrt5201", request, MedicalInfoQueryResult.class);
    }

    @Override
    public MedicalInfoQueryResult queryDise5202(QueryDise5202Request request) {
        return remoteClient.post(SERVICE, "queryDise5202", request, MedicalInfoQueryResult.class);
    }

    @Override
    public Setl5203Result querySetl5203(QuerySetl5203Request request) {
        return remoteClient.post(SERVICE, "querySetl5203", request, Setl5203Result.class);
    }

    @Override
    public MedicalInfoQueryResult queryFee5204(QueryFee5204Request request) {
        return remoteClient.post(SERVICE, "queryFee5204", request, MedicalInfoQueryResult.class);
    }

    @Override
    public MedicalInfoQueryResult queryOpspDrug5205(QueryOpspDrug5205Request request) {
        return remoteClient.post(SERVICE, "queryOpspDrug5205", request, MedicalInfoQueryResult.class);
    }

    @Override
    public MedicalInfoQueryResult queryCum5206(QueryCum5206Request request) {
        return remoteClient.post(SERVICE, "queryCum5206", request, MedicalInfoQueryResult.class);
    }

    @Override
    public MedicalInfoQueryResult queryOpspFiling5301(QueryOpspFiling5301Request request) {
        return remoteClient.post(SERVICE, "queryOpspFiling5301", request, MedicalInfoQueryResult.class);
    }
}
