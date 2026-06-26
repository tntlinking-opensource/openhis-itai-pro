/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.facade;

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

import java.util.List;

/** 吉林进销存能力（Wave I–IV）。 */
public interface ChsInventoryOperations {

    List<InvUpload3505Result> uploadSale3505(UploadSale3505Request request);

    List<InvUpload3506Result> uploadSaleReturn3506(UploadSaleReturn3506Request request);

    List<InvUpload3503Result> uploadPurchase3503(UploadPurchase3503Request request);

    List<InvUpload3504Result> uploadPurchaseReturn3504(UploadPurchaseReturn3504Request request);

    List<InvUpload3501Result> uploadStocktake3501(UploadStocktake3501Request request);

    List<InvUpload3502Result> uploadInvChange3502(UploadInvChange3502Request request);

    InitializeInventoryResult initializeInventory(InitializeInventoryRequest request);

    InvDelete3507Result deleteInvData3507(DeleteInvData3507Request request);

    List<LocalInvUploadDto> listLocalUploadRecords(ListLocalInvUploadRecordsRequest request);

    RetryPendingInvUploadsResult retryPendingUploads(RetryPendingInvUploadsRequest request);

    PlatformInvQueryResult queryPlatformInventory3508(QueryPlatformInvRequest request);

    PlatformInvQueryResult queryPlatformInvChange3509(QueryPlatformInvRequest request);

    PlatformInvQueryResult queryPlatformPurchase3510(QueryPlatformInvRequest request);

    PlatformInvQueryResult queryPlatformSale3511(QueryPlatformInvRequest request);

    PlatformInvQueryResult queryPlatformInboundTrace3512(QueryPlatformInvRequest request);

    PlatformInvQueryResult queryPlatformSaleTrace3513(QueryPlatformInvRequest request);

    CompareLocalVsPlatformResult compareLocalVsPlatform(CompareLocalVsPlatformRequest request);
}
