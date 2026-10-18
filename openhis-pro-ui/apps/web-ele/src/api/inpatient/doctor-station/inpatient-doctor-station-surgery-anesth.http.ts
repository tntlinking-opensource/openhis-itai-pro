import type {
  AnesthesiaApplyFormHeaderDto,
  AnesthesiaApplyFormLineDto,
  AnesthesiaApplyFormLineRequest,
  ClinicalProcV3SearchItemDto,
  InitSurgeryAnesthResponse,
  PlannedSurgeryFieldsDto,
  SurgeryAnesthFormKind,
  SurgeryAnesthSideCode,
  SurgeryApplyFormHeaderDto,
  SurgeryApplyFormLineDto,
  SurgeryApplyFormLineRequest,
  SurchargeActivityDefinitionItemDto,
} from './inpatient-doctor-station-surgery-anesth.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/inpatientDoctorStationSurgeryAnesth';

function post<TReq, TRes>(method: string, body: TReq) {
  return publicRequestClient.post<TRes>(`${BASE}/${method}`, body);
}

/** 进页加载手术级别、切口、麻醉方法下拉 */
export function httpInitSurgeryAnesth(body: { encounterId: string }) {
  return post<{ encounterId: string }, InitSurgeryAnesthResponse>('initSurgeryAnesth', body);
}

/** 当前就诊的手术申请单列表 */
export function httpListSurgeryApplyForms(body: { encounterId: string }) {
  return post<{ encounterId: string }, { forms?: SurgeryApplyFormHeaderDto[] }>(
    'listSurgeryApplyForms',
    body,
  );
}

/** 手术申请单详情 */
export function httpGetSurgeryApplyFormDetail(body: {
  encounterId: string;
  prescriptionNo: string;
}) {
  return post<
    { encounterId: string; prescriptionNo: string },
    {
      header?: SurgeryApplyFormHeaderDto;
      lines?: SurgeryApplyFormLineDto[];
      planned?: PlannedSurgeryFieldsDto;
    }
  >('getSurgeryApplyFormDetail', body);
}

/** 术式目录检索（关键字可空，打开下拉即可分页浏览） */
export function httpSearchClinicalProcV3(body: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return post<
    typeof body,
    { items?: ClinicalProcV3SearchItemDto[]; totalCount?: number }
  >('searchClinicalProcV3', body);
}

/** 按手术/麻醉侧检索选品：MAIN 主项 AD、EXTRA 附加诊疗、MEDICATION 药品 */
export function httpSearchSurchargeActivityDefinition(body: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  sideCode: SurgeryAnesthSideCode;
  /** MAIN / EXTRA / MEDICATION；空=MAIN */
  itemKindCode?: 'EXTRA' | 'MAIN' | 'MEDICATION';
}) {
  return post<typeof body, { items?: SurchargeActivityDefinitionItemDto[]; totalCount?: number }>(
    'searchSurchargeActivityDefinition',
    body,
  );
}

/** 保存手术申请草稿（编辑须带旧申请号，成功后改用返回的新号） */
export function httpSaveSurgeryApplyForm(body: {
  encounterId: string;
  previousPrescriptionNo?: string;
  lines: SurgeryApplyFormLineRequest[];
  planned?: PlannedSurgeryFieldsDto;
  /** 勾选需要麻醉时 true；须同时带 anesthesiaLines */
  nestAnesthesia?: boolean;
  /** 嵌套麻醉明细（与独立麻醉页同构） */
  anesthesiaLines?: AnesthesiaApplyFormLineRequest[];
}) {
  return post<
    typeof body,
    {
      prescriptionNo?: string;
      serviceRequestIds?: string[];
      nestedAnesthesiaPrescriptionNo?: string;
    }
  >('saveSurgeryApplyForm', body);
}

/** 签发手术申请。签发码仅做非空校验，由前端生成占位 */
export function httpSignSurgeryApplyForm(body: {
  encounterId: string;
  prescriptionNo: string;
  signCode: string;
}) {
  return post<typeof body, { prescriptionNo?: string; signedServiceRequestIds?: string[] }>(
    'signSurgeryApplyForm',
    body,
  );
}

/** 撤回已签发且未执行的手术申请 */
export function httpWithdrawSurgeryApplyForm(body: {
  encounterId: string;
  prescriptionNo: string;
}) {
  return post<typeof body, { prescriptionNo?: string; withdrawnServiceRequestIds?: string[] }>(
    'withdrawSurgeryApplyForm',
    body,
  );
}

/** 删除草稿手术申请（一并清理嵌套麻醉草稿） */
export function httpDeleteSurgeryApplyForm(body: {
  encounterId: string;
  prescriptionNo: string;
}) {
  return post<typeof body, { prescriptionNo?: string; deletedServiceRequestIds?: string[] }>(
    'deleteSurgeryApplyForm',
    body,
  );
}

/** 已保存的手术单勾选或取消嵌套麻醉 */
export function httpToggleAnesthesiaNest(body: {
  encounterId: string;
  surgeryPrescriptionNo: string;
  enabled: boolean;
}) {
  return post<
    typeof body,
    { surgeryPrescriptionNo?: string; anesthesiaPrescriptionNo?: string; enabled?: boolean }
  >('toggleAnesthesiaNest', body);
}

/** 当前就诊的麻醉申请单列表 */
export function httpListAnesthesiaApplyForms(body: { encounterId: string }) {
  return post<{ encounterId: string }, { forms?: AnesthesiaApplyFormHeaderDto[] }>(
    'listAnesthesiaApplyForms',
    body,
  );
}

/** 麻醉申请单详情 */
export function httpGetAnesthesiaApplyFormDetail(body: {
  encounterId: string;
  prescriptionNo: string;
}) {
  return post<
    { encounterId: string; prescriptionNo: string },
    {
      header?: AnesthesiaApplyFormHeaderDto;
      lines?: AnesthesiaApplyFormLineDto[];
      planned?: PlannedSurgeryFieldsDto;
    }
  >('getAnesthesiaApplyFormDetail', body);
}

/** 保存麻醉申请草稿 */
export function httpSaveAnesthesiaApplyForm(body: {
  encounterId: string;
  previousPrescriptionNo?: string;
  linkedSurgeryPrescriptionNo?: string;
  lines: AnesthesiaApplyFormLineRequest[];
  planned?: PlannedSurgeryFieldsDto;
}) {
  return post<typeof body, { prescriptionNo?: string; serviceRequestIds?: string[] }>(
    'saveAnesthesiaApplyForm',
    body,
  );
}

/** 签发麻醉申请 */
export function httpSignAnesthesiaApplyForm(body: {
  encounterId: string;
  prescriptionNo: string;
  signCode: string;
}) {
  return post<typeof body, { prescriptionNo?: string; signedServiceRequestIds?: string[] }>(
    'signAnesthesiaApplyForm',
    body,
  );
}

/** 撤回已签发且未执行的麻醉申请 */
export function httpWithdrawAnesthesiaApplyForm(body: {
  encounterId: string;
  prescriptionNo: string;
}) {
  return post<typeof body, { prescriptionNo?: string; withdrawnServiceRequestIds?: string[] }>(
    'withdrawAnesthesiaApplyForm',
    body,
  );
}

/** 删除草稿麻醉申请 */
export function httpDeleteAnesthesiaApplyForm(body: {
  encounterId: string;
  prescriptionNo: string;
}) {
  return post<typeof body, { prescriptionNo?: string; deletedServiceRequestIds?: string[] }>(
    'deleteAnesthesiaApplyForm',
    body,
  );
}

export function httpGetPlannedSurgeryFields(body: {
  encounterId: string;
  prescriptionNo: string;
  formKindCode: SurgeryAnesthFormKind;
}) {
  return post<typeof body, { planned?: PlannedSurgeryFieldsDto }>('getPlannedSurgeryFields', body);
}
