/**
 * 结算清单只读预览 API（MrmsSettleListPreviewAppService）
 */

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsSettleListPreview';

/** 诊断双码预览行 */
export interface SettleListDiagnosisPreviewDto {
  diagTypeCode?: string;
  diagTypeLabel?: string;
  /** 中西医维度码 WESTERN / TCM */
  diagnosisKind?: string;
  /** 中西医展示：西医 / 中医 */
  diagnosisKindLabel?: string;
  sortNo?: number;
  clinicCode?: string;
  /** 国临目录主键（对码写回；雪花字符串） */
  clinicalDefinitionId?: string;
  ybNo?: string;
  outboundCode?: string;
  mapped?: boolean;
  unmappedHint?: string;
  diagName?: string;
  admCondCode?: string;
  /** 诊断转归/病情（1治愈 2好转 3未愈 4死亡 9其他） */
  outIllStatusCode?: string;
  ybName?: string;
  confirmTime?: string;
  diagRemark?: string;
  maindiagFlag?: string;
  /**
   * 是否上传医保（1是 / 0否；对齐首页 need_yb_up）。
   * 出院缺省按 1；0 时 4101A/4401 不上送该行。
   */
  needYbUp?: string;
}

/** 手术双码预览行（列对齐老 InHosDrgOperation） */
export interface SettleListProcedurePreviewDto {
  sortNo?: number;
  clinicCode?: string;
  ybNo?: string;
  outboundCode?: string;
  mapped?: boolean;
  unmappedHint?: string;
  drgExtCode?: string;
  oprnOprtName?: string;
  mainOprnFlag?: string;
  oprnDate?: string;
  oprnLevelCode?: string;
  oprnIncisionCode?: string;
  healGradeCode?: string;
  anstWayCode?: string;
  operDrName?: string;
  anstDrName?: string;
}

/** 结算清单预览响应 */
export interface LoadSettleListPreviewResult {
  caseRecordId?: string;
  encounterId?: string;
  patientId?: string;
  medcasno?: string;
  iptNo?: string;
  psnName?: string;
  admCatyName?: string;
  admTime?: string;
  dscgTime?: string;
  spgaNurscareDays?: number;
  lv1NurscareDays?: number;
  scdNurscareDays?: number;
  lv3NurscareDays?: number;
  settleUploadStatusCode?: string;
  setlListId?: string;
  /** 医保人员编号 */
  psnNo?: string;
  /** 医保结算 ID */
  setlId?: string;
  diagnoses?: SettleListDiagnosisPreviewDto[];
  procedures?: SettleListProcedurePreviewDto[];
  icuinfo?: unknown[];
  bldinfo?: unknown[];
  iteminfo?: unknown[];
  assembledPreview?: Record<string, unknown>;
  assembleHint?: string;
}

/** 预览入参 */
export interface LoadSettleListPreviewParams {
  caseRecordId?: string;
  encounterId?: string;
  contractBusNo?: string;
}

/** 加载结算清单只读预览（双码 + 分区占位 + 4101A 组装） */
export async function loadSettleListPreviewApi(params: LoadSettleListPreviewParams) {
  return publicRequestClient.post<LoadSettleListPreviewResult>(`${BASE}/loadSettleListPreview`, {
    caseRecordId: params.caseRecordId ? preserveApiIdForRequest(params.caseRecordId) : undefined,
    encounterId: params.encounterId ? preserveApiIdForRequest(params.encounterId) : undefined,
    contractBusNo: params.contractBusNo,
  });
}
