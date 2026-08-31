import type {
  DoctorStationAdviceDraftLineRequest,
  ResolveDoctorStationAdviceDraftBindsRequest,
  ResolveDoctorStationAdviceDraftBindsResponse,
  SaveDoctorStationAdviceDraftBatchRequest,
  SaveDoctorStationAdviceDraftBatchResponse,
} from '#/api/doctor-station/advice.types';

import {
  httpListDoctorStationAdvice,
  httpResolveDoctorStationAdviceDraftBinds,
} from '#/api/doctor-station/advice.http';
import {
  httpListInpatientDoctorStationAdvice,
  httpResolveInpatientDoctorStationAdviceDraftBinds,
  httpSaveInpatientDoctorStationAdviceDraftBatch,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';

import { saveDoctorStationAdviceDraftBatchWithSkinTestCrossLotConfirm } from './advice-skin-test-cross-lot-confirm';

/** IF-DOR-23 / IF-IP-DOR-23 绑定解析 HTTP 适配（门诊 / 住院） */
export interface AdviceDraftBindHttpAdapter {
  resolveDraftBinds: (
    body: ResolveDoctorStationAdviceDraftBindsRequest,
  ) => Promise<ResolveDoctorStationAdviceDraftBindsResponse>;
  saveDraftBatch: (
    body: SaveDoctorStationAdviceDraftBatchRequest,
  ) => Promise<SaveDoctorStationAdviceDraftBatchResponse>;
  listAdvice: (body: { encounterId: string; patientId: string }) => Promise<unknown>;
}

/** 门诊医生站绑定解析适配 */
export const outpatientAdviceDraftBindHttpAdapter: AdviceDraftBindHttpAdapter = {
  resolveDraftBinds: httpResolveDoctorStationAdviceDraftBinds,
  saveDraftBatch: saveDoctorStationAdviceDraftBatchWithSkinTestCrossLotConfirm,
  listAdvice: httpListDoctorStationAdvice,
};

/** 住院医生站绑定解析适配（无门诊皮试跨批确认） */
export const inpatientAdviceDraftBindHttpAdapter: AdviceDraftBindHttpAdapter = {
  resolveDraftBinds: httpResolveInpatientDoctorStationAdviceDraftBinds,
  saveDraftBatch: httpSaveInpatientDoctorStationAdviceDraftBatch,
  listAdvice: httpListInpatientDoctorStationAdvice,
};

export type { DoctorStationAdviceDraftLineRequest };
