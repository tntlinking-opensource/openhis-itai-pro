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

/** 国临术式检索 */
export function httpSearchClinicalProcV3(body: { keyword: string }) {
  return post<{ keyword: string }, { items?: ClinicalProcV3SearchItemDto[] }>(
    'searchClinicalProcV3',
    body,
  );
}

/** 按手术侧或麻醉侧检索诊疗项目 */
export function httpSearchSurchargeActivityDefinition(body: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  sideCode: SurgeryAnesthSideCode;
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
  nestAnesthesia?: boolean;
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

export function httpGetPlannedSurgeryFields(body: {
  encounterId: string;
  prescriptionNo: string;
  formKindCode: SurgeryAnesthFormKind;
}) {
  return post<typeof body, { planned?: PlannedSurgeryFieldsDto }>('getPlannedSurgeryFields', body);
}
