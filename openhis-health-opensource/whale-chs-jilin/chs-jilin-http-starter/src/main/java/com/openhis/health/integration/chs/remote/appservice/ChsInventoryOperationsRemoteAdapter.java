package com.openhis.health.integration.chs.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openhis.chs.domain.inventory.dto.request.CompareLocalVsPlatformRequest;
import com.openhis.chs.domain.inventory.dto.request.ListLocalInvUploadRecordsRequest;
import com.openhis.chs.domain.inventory.dto.request.QueryPlatformInvRequest;
import com.openhis.chs.domain.inventory.dto.request.RetryPendingInvUploadsRequest;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchase3503Request;
import com.openhis.chs.domain.inventory.dto.request.UploadPurchaseReturn3504Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSale3505Request;
import com.openhis.chs.domain.inventory.dto.request.UploadSaleReturn3506Request;
import com.openhis.chs.domain.inventory.dto.request.InitializeInventoryRequest;
import com.openhis.chs.domain.inventory.dto.request.UploadStocktake3501Request;
import com.openhis.chs.domain.inventory.dto.request.UploadInvChange3502Request;
import com.openhis.chs.domain.inventory.dto.request.DeleteInvData3507Request;
import com.openhis.chs.domain.inventory.dto.response.InvDelete3507Result;
import com.openhis.chs.domain.inventory.dto.response.InitializeInventoryResult;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3501Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3502Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3503Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3504Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3505Result;
import com.openhis.chs.domain.inventory.dto.response.InvUpload3506Result;
import com.openhis.chs.domain.inventory.dto.response.CompareLocalVsPlatformResult;
import com.openhis.chs.domain.inventory.dto.response.LocalInvUploadDto;
import com.openhis.chs.domain.inventory.dto.response.PlatformInvQueryResult;
import com.openhis.chs.domain.inventory.dto.response.RetryPendingInvUploadsResult;
import com.openhis.chs.domain.inventory.facade.ChsInventoryOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsInventoryOperationsRemoteAdapter implements ChsInventoryOperations {

    private static final String SERVICE = "chsInventory";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public List<InvUpload3505Result> uploadSale3505(UploadSale3505Request request) {
        return remoteClient.post(
            SERVICE,
            "uploadSale3505",
            request,
            new TypeReference<List<InvUpload3505Result>>() {});
    }

    @Override
    public List<InvUpload3506Result> uploadSaleReturn3506(UploadSaleReturn3506Request request) {
        return remoteClient.post(
            SERVICE,
            "uploadSaleReturn3506",
            request,
            new TypeReference<List<InvUpload3506Result>>() {});
    }

    @Override
    public List<InvUpload3503Result> uploadPurchase3503(UploadPurchase3503Request request) {
        return remoteClient.post(
            SERVICE,
            "uploadPurchase3503",
            request,
            new TypeReference<List<InvUpload3503Result>>() {});
    }

    @Override
    public List<InvUpload3504Result> uploadPurchaseReturn3504(UploadPurchaseReturn3504Request request) {
        return remoteClient.post(
            SERVICE,
            "uploadPurchaseReturn3504",
            request,
            new TypeReference<List<InvUpload3504Result>>() {});
    }

    @Override
    public List<InvUpload3501Result> uploadStocktake3501(UploadStocktake3501Request request) {
        return remoteClient.post(
            SERVICE,
            "uploadStocktake3501",
            request,
            new TypeReference<List<InvUpload3501Result>>() {});
    }

    @Override
    public List<InvUpload3502Result> uploadInvChange3502(UploadInvChange3502Request request) {
        return remoteClient.post(
            SERVICE,
            "uploadInvChange3502",
            request,
            new TypeReference<List<InvUpload3502Result>>() {});
    }

    @Override
    public InitializeInventoryResult initializeInventory(InitializeInventoryRequest request) {
        return remoteClient.post(
            SERVICE,
            "initializeInventory",
            request,
            new TypeReference<InitializeInventoryResult>() {});
    }

    @Override
    public InvDelete3507Result deleteInvData3507(DeleteInvData3507Request request) {
        return remoteClient.post(
            SERVICE,
            "deleteInvData3507",
            request,
            new TypeReference<InvDelete3507Result>() {});
    }

    @Override
    public List<LocalInvUploadDto> listLocalUploadRecords(ListLocalInvUploadRecordsRequest request) {
        return remoteClient.post(
            SERVICE,
            "listLocalUploadRecords",
            request,
            new TypeReference<List<LocalInvUploadDto>>() {});
    }

    @Override
    public RetryPendingInvUploadsResult retryPendingUploads(RetryPendingInvUploadsRequest request) {
        return remoteClient.post(
            SERVICE,
            "retryPendingUploads",
            request,
            new TypeReference<RetryPendingInvUploadsResult>() {});
    }

    @Override
    public PlatformInvQueryResult queryPlatformInventory3508(QueryPlatformInvRequest request) {
        return remoteClient.post(
            SERVICE,
            "queryPlatformInventory3508",
            request,
            new TypeReference<PlatformInvQueryResult>() {});
    }

    @Override
    public PlatformInvQueryResult queryPlatformInvChange3509(QueryPlatformInvRequest request) {
        return remoteClient.post(
            SERVICE,
            "queryPlatformInvChange3509",
            request,
            new TypeReference<PlatformInvQueryResult>() {});
    }

    @Override
    public PlatformInvQueryResult queryPlatformPurchase3510(QueryPlatformInvRequest request) {
        return remoteClient.post(
            SERVICE,
            "queryPlatformPurchase3510",
            request,
            new TypeReference<PlatformInvQueryResult>() {});
    }

    @Override
    public PlatformInvQueryResult queryPlatformSale3511(QueryPlatformInvRequest request) {
        return remoteClient.post(
            SERVICE,
            "queryPlatformSale3511",
            request,
            new TypeReference<PlatformInvQueryResult>() {});
    }

    @Override
    public PlatformInvQueryResult queryPlatformInboundTrace3512(QueryPlatformInvRequest request) {
        return remoteClient.post(
            SERVICE,
            "queryPlatformInboundTrace3512",
            request,
            new TypeReference<PlatformInvQueryResult>() {});
    }

    @Override
    public PlatformInvQueryResult queryPlatformSaleTrace3513(QueryPlatformInvRequest request) {
        return remoteClient.post(
            SERVICE,
            "queryPlatformSaleTrace3513",
            request,
            new TypeReference<PlatformInvQueryResult>() {});
    }

    @Override
    public CompareLocalVsPlatformResult compareLocalVsPlatform(CompareLocalVsPlatformRequest request) {
        return remoteClient.post(
            SERVICE,
            "compareLocalVsPlatform",
            request,
            new TypeReference<CompareLocalVsPlatformResult>() {});
    }
}
