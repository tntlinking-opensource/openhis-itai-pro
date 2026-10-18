import type { ApiOptionItem } from '#/types/api-response';

import { getCodeListApi, getConceptListApi } from '#/api/basic/code';
import { httpInitSurgeryAnesth } from '#/api/inpatient/doctor-station/inpatient-doctor-station-surgery-anesth.http';
import { httpLoadNurseStationSkinTestInit } from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.http';
import { httpListPractitioners } from '#/api/outpatient-registration';
import { TerminologyCodeSystemName } from '#/constants/domain/terminology-code-system';

interface CodeSystemRow {
  id?: string;
  name?: string;
}

interface ConceptRow {
  code?: string;
  display?: string;
  isActive?: boolean;
  name?: string;
  sortOrder?: number | string;
}

/** 病案首页手术行：级别 / 切口愈合 / 麻醉方法 / 术者助手 / 护士 / 麻醉医师 */
export interface HomepageProcedureOptionsBundle {
  levelOptions: ApiOptionItem[];
  /** 切口类别与愈合等级共用医保组合码系 */
  incisionHealOptions: ApiOptionItem[];
  anesthesiaMethodOptions: ApiOptionItem[];
  /** 术者 / 一助 / 二助（出诊医生） */
  practitionerOptions: ApiOptionItem[];
  /** 配合护士（ROLE_NURSE） */
  nurseOptions: ApiOptionItem[];
  /** 麻醉医师（麻醉室医师岗；未配置时回落出诊医生） */
  anesthetistOptions: ApiOptionItem[];
}

/** 从分页或数组响应中取出代码系统行列表 */
function unwrapCodeSystemRows(response: unknown): CodeSystemRow[] {
  if (Array.isArray(response)) {
    return response;
  }
  if (response && typeof response === 'object') {
    const obj = response as { content?: CodeSystemRow[]; data?: { content?: CodeSystemRow[] } };
    return obj.content ?? obj.data?.content ?? [];
  }
  return [];
}

/** 从概念列表响应中取出行 */
function unwrapConceptRows(response: unknown): ConceptRow[] {
  if (Array.isArray(response)) {
    return response;
  }
  if (response && typeof response === 'object') {
    const obj = response as { data?: ConceptRow[] };
    return obj.data ?? [];
  }
  return [];
}

/**
 * 人员选项：首页只落姓名，下拉 value 用姓名。
 *
 * @param raw 接口选项
 */
function mapNameOptions(
  raw: Array<{ label?: null | string; value?: null | number | string }> | undefined,
): ApiOptionItem[] {
  return (raw ?? [])
    .map((item) => {
      const label = String(item.label ?? '').trim();
      return { label, value: label };
    })
    .filter((item) => item.label);
}

/**
 * 按术语体系 name 加载启用态选项（与手术申请 init 同源种子）。
 *
 * @param codeSystemName ter_code_system.name
 */
async function loadOptionsByCodeSystemName(codeSystemName: string): Promise<ApiOptionItem[]> {
  const response = await getCodeListApi({
    pageNo: 1,
    pageSize: 200,
    name: codeSystemName,
  });
  const systems = unwrapCodeSystemRows(response);
  const system = systems.find((item) => item.name === codeSystemName);
  if (!system?.id) {
    return [];
  }
  const conceptResponse = await getConceptListApi({ id: system.id });
  const concepts = unwrapConceptRows(conceptResponse);
  return concepts
    .filter((item) => item.isActive !== false)
    .map((item) => {
      const value = String(item.code ?? '').trim();
      const label = (item.display ?? item.name ?? value).trim() || value;
      const sortRaw = Number(item.sortOrder ?? 0);
      return {
        label,
        value,
        sort: Number.isFinite(sortRaw) ? sortRaw : 0,
      };
    })
    .filter((item) => item.value)
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.value.localeCompare(b.value))
    .map((item) => ({ label: item.label, value: item.value }));
}

/**
 * 加载病案首页手术行下拉。
 * 字典对齐手术申请；护士=护士岗；麻醉医师=麻醉室医师岗（同 initSurgeryAnesth）。
 *
 * @param encounterId 住院就诊主键；有则拉麻醉室医师池
 */
export async function loadHomepageProcedureOptions(
  encounterId?: string,
): Promise<HomepageProcedureOptionsBundle> {
  const encounter = String(encounterId ?? '').trim();
  const [levelOptions, incisionHealOptions, anesthesiaMethodOptions, practitionerRes, nurseInit, anesthInit] =
    await Promise.all([
      loadOptionsByCodeSystemName(TerminologyCodeSystemName.OPRN_OPRT_LV_CODE),
      loadOptionsByCodeSystemName(TerminologyCodeSystemName.SINC_HEAL_LV_CODE),
      loadOptionsByCodeSystemName(TerminologyCodeSystemName.ANST_MTD_CODE),
      httpListPractitioners({}),
      httpLoadNurseStationSkinTestInit().catch(() => null),
      encounter
        ? httpInitSurgeryAnesth({ encounterId: encounter }).catch(() => null)
        : Promise.resolve(null),
    ]);
  const practitionerOptions = mapNameOptions(practitionerRes.practitionerOptions).filter(
    (item) => item.label.trim().toLowerCase() !== 'admin',
  );
  const nurseOptions = mapNameOptions(nurseInit?.nurseOptions ?? undefined).filter(
    (item) => item.label.trim().toLowerCase() !== 'admin',
  );
  const fromAnesthRoom = mapNameOptions(anesthInit?.anesthetistOptions ?? undefined);
  // 与手术申请单同口径：麻醉室池空则回落出诊医生；业务下拉剔除平台 admin
  const pool = fromAnesthRoom.length > 0 ? fromAnesthRoom : practitionerOptions;
  const anesthetistOptions = pool.filter(
    (item) => item.label.trim().toLowerCase() !== 'admin',
  );
  return {
    levelOptions,
    incisionHealOptions,
    anesthesiaMethodOptions,
    practitionerOptions,
    nurseOptions,
    anesthetistOptions,
  };
}
