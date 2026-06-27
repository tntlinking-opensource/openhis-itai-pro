import type {
  CaseRecordDetailDto,
  DiagnosisLineDto,
  HomepageDto,
  ProcedureLineDto,
  RefreshSectionCode,
  SaveHomepageParams,
} from '#/api/mrms/types';

import dayjs from 'dayjs';

/** 病案室角色关键字（roles 命中其一视为病案室视图） */
export function detectRecordRoomRole(roles: string[] | undefined): boolean {
  if (!roles?.length) {
    return false;
  }
  return roles.some((r) => /mrms|病案|record|archive/i.test(String(r)));
}

/** 列表默认查询：医生 vs 病案室 */
export function buildDefaultListQuery(
  isRecordRoom: boolean,
  admCatyName: string,
  attendingPractitionerId?: string,
) {
  const today = dayjs();
  if (isRecordRoom) {
    return {
      pageNo: 1,
      pageSize: 20,
      statusCode: undefined as string | undefined,
      statusCodes: ['submitted', 'received'] as string[],
      dscgFrom: today.subtract(7, 'day').format('YYYY-MM-DD'),
      dscgTo: today.format('YYYY-MM-DD'),
    };
  }
  return {
    pageNo: 1,
    pageSize: 20,
    admCatyName: admCatyName || undefined,
    attendingPractitionerId: attendingPractitionerId || undefined,
    statusCode: undefined as string | undefined,
    statusCodes: ['draft', 'returned'] as string[],
    dscgFrom: today.subtract(30, 'day').format('YYYY-MM-DD'),
    dscgTo: today.format('YYYY-MM-DD'),
  };
}

/** 首页表字段为空时，用病案主档冗余字段补全展示（不落库，仅首屏） */
export function mergeCaseRecordIntoFormModel(
  form: SaveHomepageParams,
  detail: CaseRecordDetailDto | null | undefined,
): SaveHomepageParams {
  if (!detail) {
    return form;
  }
  return {
    ...form,
    caseRecordId: form.caseRecordId || String(detail.caseRecordId ?? ''),
  };
}

/** 判断首页是否尚未从 HIS/人工写入过有效内容 */
export function isHomepageContentEmpty(homepage: HomepageDto | null | undefined): boolean {
  if (!homepage) {
    return true;
  }
  const scalarFilled =
    homepage.psnName || homepage.iptNo || homepage.admTime || homepage.dscgTime || homepage.certno;
  const rowsFilled =
    (homepage.diagnoses?.length ?? 0) > 0 || (homepage.procedures?.length ?? 0) > 0;
  return !scalarFilled && !rowsFilled;
}

function extensionField(homepage: HomepageDto, key: keyof SaveHomepageParams): string | undefined {
  const ext = homepage.extension;
  if (ext && key in ext) {
    const v = ext[key as string];
    if (v !== null && v !== undefined && v !== '') {
      return String(v);
    }
  }
  return undefined;
}

export function homepageToFormModel(homepage: HomepageDto): SaveHomepageParams {
  return {
    caseRecordId: String(homepage.caseRecordId ?? ''),
    psnName: homepage.psnName,
    gendCode: homepage.gendCode,
    brdy: homepage.brdy,
    certno: homepage.certno,
    ntlyCode: homepage.ntlyCode ?? extensionField(homepage, 'ntlyCode'),
    natyCode: homepage.natyCode ?? extensionField(homepage, 'natyCode'),
    prfsCode: homepage.prfsCode ?? extensionField(homepage, 'prfsCode'),
    mrgStasCode: homepage.mrgStasCode ?? extensionField(homepage, 'mrgStasCode'),
    currAddr: homepage.currAddr ?? extensionField(homepage, 'currAddr'),
    psnTel: homepage.psnTel ?? extensionField(homepage, 'psnTel'),
    conerName: homepage.conerName ?? extensionField(homepage, 'conerName'),
    conerTel: homepage.conerTel ?? extensionField(homepage, 'conerTel'),
    conerAddr: homepage.conerAddr ?? extensionField(homepage, 'conerAddr'),
    iptNo: homepage.iptNo,
    admWayCode: homepage.admWayCode,
    admCatyCode: homepage.admCatyCode,
    admCatyName: homepage.admCatyName ?? extensionField(homepage, 'admCatyName'),
    dscgCatyCode: homepage.dscgCatyCode,
    dscgCatyName: homepage.dscgCatyName ?? extensionField(homepage, 'dscgCatyName'),
    admTime: homepage.admTime,
    dscgTime: homepage.dscgTime,
    iptDays: homepage.iptDays,
    dscgWayCode: homepage.dscgWayCode,
    transferDeptSummary:
      homepage.transferDeptSummary ?? extensionField(homepage, 'transferDeptSummary'),
    deptDirectorName: homepage.deptDirectorName ?? extensionField(homepage, 'deptDirectorName'),
    chfdrName: homepage.chfdrName,
    atddrName: homepage.atddrName,
    iptDrName: homepage.iptDrName,
    respNursName: homepage.respNursName ?? extensionField(homepage, 'respNursName'),
    extension: homepage.extension,
    diagnoses: cloneDiagnoses(homepage.diagnoses),
    procedures: cloneProcedures(homepage.procedures),
  };
}

function cloneDiagnoses(rows?: DiagnosisLineDto[]): DiagnosisLineDto[] {
  if (!rows?.length) {
    return [];
  }
  return rows.map((r) => ({ ...r }));
}

function cloneProcedures(rows?: ProcedureLineDto[]): ProcedureLineDto[] {
  if (!rows?.length) {
    return [];
  }
  return rows.map((r) => ({ ...r }));
}

export function newDiagnosisRow(diagTypeCode: string, sortNo: number): DiagnosisLineDto {
  return { diagTypeCode, sortNo, diagCode: '', diagName: '', admCondCode: '' };
}

export function newProcedureRow(sortNo: number): ProcedureLineDto {
  return { sortNo, oprnOprtCode: '', oprnOprtName: '' };
}

export function sectionToRefreshCode(section: string): RefreshSectionCode {
  if (
    section === 'basic' ||
    section === 'inpatient' ||
    section === 'diagnosis' ||
    section === 'procedure' ||
    section === 'staff'
  ) {
    return section;
  }
  return 'basic';
}
