/**
 * 门诊 / 住院医生站 · IF-DOR-23 / 住院同构绑定解析 HTTP 适配。
 *
 * <p>门诊走真实解析；住院开立恒空（BR-IP-ORD-036），未合管模板仅护士执行挂载。</p>
 */
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
  httpSaveInpatientDoctorStationAdviceDraftBatch,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';

import { saveDoctorStationAdviceDraftBatchWithSkinTestCrossLotConfirm } from './advice-skin-test-cross-lot-confirm';

/** IF-DOR-23 / 住院同构绑定解析 HTTP 适配（门诊 / 住院） */
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

/**
 * 住院开立：不解析任何绑定（诊疗/耗材/合管均不带，BR-IP-ORD-036）。
 *
 * @param _body 与门诊同构请求体；本适配器不发 HTTP
 * @returns 恒为空 bindItems
 */
async function resolveInpatientDraftBindsNone(
  _body: ResolveDoctorStationAdviceDraftBindsRequest,
): Promise<ResolveDoctorStationAdviceDraftBindsResponse> {
  // 未合管模板由护士执行列表挂载；合管执行侧亦不带
  return { bindItems: [] };
}

/** 住院医生站绑定解析适配（开立侧恒空；无门诊皮试跨批确认） */
export const inpatientAdviceDraftBindHttpAdapter: AdviceDraftBindHttpAdapter = {
  resolveDraftBinds: resolveInpatientDraftBindsNone,
  saveDraftBatch: httpSaveInpatientDoctorStationAdviceDraftBatch,
  listAdvice: httpListInpatientDoctorStationAdvice,
};

export type { DoctorStationAdviceDraftLineRequest };
