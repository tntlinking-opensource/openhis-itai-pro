import type { RegisterPatientRecord } from './register.types';

import type {
  SocialCard1101Credential,
  YbEcVoucherReadResult,
  YbSocialCardReadResult,
} from '#/chs/utils/yb-plugin';

import {
  resolveEcVoucher1101Credential,
  resolveSocialCard1101Credential,
} from '#/chs/utils/yb-plugin';
import { calcAgeFromBirthDate, parseChineseIdCard18 } from '#/utils/format/id-card-cn';

/** 患者身份读取媒介（身份证 / 医保卡 / 医保码） */
export type PatientIdentitySource = 'id' | 'yb-card' | 'yb-ec';

export const PATIENT_IDENTITY_SOURCE_LABEL: Record<PatientIdentitySource, string> = {
  id: '身份证',
  'yb-card': '医保卡',
  'yb-ec': '医保码',
};

/** 读卡/扫码后的统一身份快照（匹配患者、回填表单、拉医保待遇共用） */
export interface PatientIdentitySnapshot {
  source: PatientIdentitySource;
  sourceLabel: string;
  /** 18 位身份证号（若有） */
  certno: string;
  patientName: string;
  /** 用于患者档案检索（证件号或卡号） */
  patientMatchKeyword: string;
  /** 参保地/发卡地，供费用性质推荐与 1101 路由 */
  insuplcAdmdvs: string;
  /** 有值时可走 1101 待遇拉取 */
  insuranceCredential?: SocialCard1101Credential;
}

export interface InsuranceReadRefs {
  lastYbCardInfo: { value: string };
  lastYbCardSn: { value: string };
  lastYbCertno: { value: string };
  lastYbPsnName: { value: string };
  lastYbIssuingAreaCode: { value: string };
  lastYbMediumType: { value: string };
}

export function buildFormRecordFromCertno(certno: string, patientName = ''): RegisterPatientRecord {
  const parsed = parseChineseIdCard18(certno);
  return {
    patientAge: parsed ? calcAgeFromBirthDate(parsed.birthDate) : '',
    patientBirthday: parsed?.birthDate ?? '',
    patientGender: parsed?.genderCode ?? '',
    patientIdCard: certno,
    patientName,
    patientPhone: '',
  };
}

export function buildFormRecordFromSnapshot(
  snapshot: PatientIdentitySnapshot,
): RegisterPatientRecord {
  if (snapshot.certno) {
    return buildFormRecordFromCertno(snapshot.certno, snapshot.patientName);
  }
  return {
    patientAge: '',
    patientBirthday: '',
    patientGender: '',
    patientIdCard: '',
    patientName: snapshot.patientName,
    patientPhone: '',
  };
}

/** 身份证读卡 → 统一快照（1101 走 02+身份证号，与省内医保卡一致） */
export function buildSnapshotFromIdCard(idCardNo: string): null | PatientIdentitySnapshot {
  const certno = idCardNo.trim().toUpperCase();
  if (!certno) {
    return null;
  }
  return {
    source: 'id',
    sourceLabel: PATIENT_IDENTITY_SOURCE_LABEL.id,
    certno,
    patientName: '',
    patientMatchKeyword: certno,
    insuplcAdmdvs: '',
    insuranceCredential: {
      mdtrtCertType: '02',
      mdtrtCertNo: certno,
      certno,
      psnCertType: '01',
    },
  };
}

/** 社保卡读卡 → 统一快照 */
export function buildSnapshotFromSocialCard(
  card: YbSocialCardReadResult,
): null | PatientIdentitySnapshot {
  const credential = resolveSocialCard1101Credential(card);
  const certno = (credential.certno ?? card.certno ?? '').trim().toUpperCase();
  const keyword = certno || (card.mdtrtCertNo ?? '').trim();
  if (!keyword) {
    return null;
  }
  return {
    source: 'yb-card',
    sourceLabel: PATIENT_IDENTITY_SOURCE_LABEL['yb-card'],
    certno,
    patientName: (card.psnName ?? '').trim(),
    patientMatchKeyword: keyword,
    insuplcAdmdvs: (card.issuingAreaCode ?? '').trim(),
    insuranceCredential: credential,
  };
}

/** 医保电子凭证 → 统一快照 */
export function buildSnapshotFromEcVoucher(
  ec: YbEcVoucherReadResult,
): null | PatientIdentitySnapshot {
  const credential = resolveEcVoucher1101Credential(ec);
  if (!credential) {
    return null;
  }
  const certno = (credential.certno ?? ec.idNo ?? '').trim().toUpperCase();
  const keyword = certno || credential.mdtrtCertNo.trim();
  if (!keyword) {
    return null;
  }
  return {
    source: 'yb-ec',
    sourceLabel: PATIENT_IDENTITY_SOURCE_LABEL['yb-ec'],
    certno,
    patientName: (ec.userName ?? '').trim(),
    patientMatchKeyword: keyword,
    insuplcAdmdvs: (ec.insuOrg ?? '').trim(),
    insuranceCredential: credential,
  };
}

/** 电子凭证 01 类型须同时具备身份证号与姓名 */
export function validateSnapshotForInsurance1101(snapshot: PatientIdentitySnapshot): null | string {
  const cred = snapshot.insuranceCredential;
  if (cred?.mdtrtCertType === '01' && (!snapshot.certno || !snapshot.patientName)) {
    return '医保码未返回身份证号或姓名，无法调用医保 1101';
  }
  if (cred?.mdtrtCertType === '03' && !(cred.cardSn ?? '').trim()) {
    return '社保卡读卡缺少卡识别码，请重新读卡';
  }
  return null;
}

/** 将统一快照写入待遇模块的医保读卡缓存 */
export function applyInsuranceSnapshotToRefs(
  snapshot: PatientIdentitySnapshot,
  refs: InsuranceReadRefs,
): void {
  const credential = snapshot.insuranceCredential;
  if (!credential) {
    return;
  }
  refs.lastYbCardInfo.value = credential.mdtrtCertNo;
  refs.lastYbCardSn.value = credential.cardSn ?? '';
  refs.lastYbCertno.value = (credential.certno ?? snapshot.certno).trim().toUpperCase();
  refs.lastYbPsnName.value = snapshot.patientName;
  refs.lastYbIssuingAreaCode.value = snapshot.insuplcAdmdvs;
  refs.lastYbMediumType.value = credential.mdtrtCertType;
}

export function snapshotHasInsuranceCredential(snapshot: PatientIdentitySnapshot): boolean {
  return Boolean(snapshot.insuranceCredential?.mdtrtCertNo);
}
