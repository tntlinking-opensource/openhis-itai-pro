import type { ComputedRef, Ref } from 'vue';

import { computed } from 'vue';

import type { PatientItem } from './types';

interface UseNurseStationPatientProjectionsOptions {
  activePatientId: Ref<null | string>;
  patients: Ref<PatientItem[]>;
  regionNameByIdRecord: ComputedRef<Record<string, string>>;
  selectedPatientIds: Ref<Set<string>>;
  wardNameByIdRecord: ComputedRef<Record<string, string>>;
}

/** 护士站患者投影：按页面语义构造右侧模块入参。 */
export function useNurseStationPatientProjections(
  options: UseNurseStationPatientProjectionsOptions,
) {
  const {
    activePatientId,
    patients,
    regionNameByIdRecord,
    selectedPatientIds,
    wardNameByIdRecord,
  } = options;

  /** 将患者主数据映射为右侧模块统一入参，避免各页重复拼装字段。 */
  function buildModulePatientPayload(item: PatientItem) {
    return {
      id: item.id,
      patientId: item.patientId,
      encounterBusNo: item.encounterBusNo,
      admissionAt: item.admissionTime ?? item.startTime,
      inWardAt: item.startTime,
      dischargeAt: item.endTime,
      bedNo: item.bedNo,
      name: item.name,
      gender: item.gender,
      age: item.age,
      nurseLevel: item.level,
      feeType: item.feeType,
      diagnosis: item.diagnosis,
      regionName: regionNameByIdRecord.value[item.regionId] || '-',
      wardName: wardNameByIdRecord.value[item.wardId] || '-',
      pendingAudit: item.pendingAudit,
      pendingExecute: item.pendingExecute,
      accountId: item.accountId,
      accountBalance: item.accountBalance,
      accountBalanceDisplayMode: item.accountBalanceDisplayMode,
      accountBalanceSummaryText: item.accountBalanceSummaryText,
      accountBalanceSubText: item.accountBalanceSubText,
      availableBalance: item.availableBalance,
      pendingSettlementTotal: item.pendingSettlementTotal,
      lowBalanceWarning: item.lowBalanceWarning,
      riskTags: item.riskText ? [item.riskText] : [],
      statusEnum: item.statusEnum,
    };
  }

  const selectedPatients = computed(() =>
    // 仅保留当前患者全集中仍存在的选中 id，避免脏状态传入子模块。
    patients.value
      .filter((item) => selectedPatientIds.value.has(item.id))
      .map((item) => buildModulePatientPayload(item)),
  );
  /** 单选页的患者入参（仅主操作患者）。 */
  const singlePanelPatients = computed(() => {
    if (!activePatientId.value) return [];
    // 按 activePatientId 定位主患者，供单选页面使用。
    const item = patients.value.find((row) => row.id === activePatientId.value);
    return item ? [buildModulePatientPayload(item)] : [];
  });
  return {
    selectedPatients,
    singlePanelPatients,
  };
}
