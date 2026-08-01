<script setup lang="ts">
import type {
  CommonDiagnosesResponse,
  CommonDiagnosisRowDto,
  EncounterDiagnosisRowDto,
  OpspFilingDiagnosisRowDto,
  PatientOpspFilingDiagnosisResponse,
} from '#/api/doctor-station/diagnosis.types';

import { computed } from 'vue';

import { ElMessage } from 'element-plus';

interface TreeNode {
  id: string;
  label: string;
  row?: CommonDiagnosisRowDto;
  opspRow?: OpspFilingDiagnosisRowDto;
  children?: TreeNode[];
}

defineOptions({ name: 'DiagnosisQuickTree' });

const props = defineProps<{
  businessClass: CommonDiagnosesResponse | null;
  /** 当前就诊已保存诊断（中医病证组已合并，与右侧表格一致） */
  displayRows: EncounterDiagnosisRowDto[];
  /** 慢特病备案；未启用时不渲染「慢病备案」分支 */
  opspFiling?: null | PatientOpspFilingDiagnosisResponse;
}>();

const emit = defineEmits<{
  append: [row: EncounterDiagnosisRowDto];
}>();

/** 快捷树叶子仅展示名称（不拼接诊断编码） */
function formatDiagnosisLeafLabel(name?: null | string): string {
  return String(name ?? '').trim();
}

/** 四类常用 + 可选慢病备案快捷树 */
const quickTreeData = computed<TreeNode[]>(() => {
  const b = props.businessClass;
  if (!b) return [];

  const mk = (
    prefix: string,
    label: string,
    list: CommonDiagnosisRowDto[] | null | undefined,
  ): TreeNode => ({
    id: prefix,
    label,
    children: (list ?? []).map((r, i) => ({
      id: `${prefix}-${i}-${r.conditionDefinitionId ?? i}-${r.syndromeDefinitionId ?? ''}`,
      label: formatDiagnosisLeafLabel(r.conditionDefinitionName ?? r.conditionName),
      row: r,
    })),
  });

  const nodes: TreeNode[] = [
    mk('hist', '历史', b.patientHistoryList),
    mk('doc', '常用', b.doctorCommonUseList),
    mk('usr', '个人', b.userPersonalList),
    mk('org', '科室', b.organizationList),
  ];

  // CHS 未启用时整支隐藏（非灰显）
  if (props.opspFiling?.opspFilingImportEnabled) {
    const filingList = props.opspFiling.filingList ?? [];
    nodes.push({
      id: 'opsp',
      label: filingList.length > 0 ? `慢病备案(${filingList.length})` : '慢病备案',
      children: filingList.map((r, i) => ({
        id: `opsp-${i}-${r.opspDiseCode ?? i}`,
        label: formatDiagnosisLeafLabel(r.conditionDefinitionName ?? r.opspDiseName),
        opspRow: r,
      })),
    });
  }

  return nodes;
});

/** 候选是否已在当前就诊列表 */
function isCommonRowAlreadySaved(src: CommonDiagnosisRowDto): boolean {
  const diseaseId = String(src.conditionDefinitionId ?? '').trim();
  const syndromeId = String(src.syndromeDefinitionId ?? '').trim();
  if (diseaseId && syndromeId) {
    return (props.displayRows ?? []).some(
      (r) => r.conditionDefinitionId === diseaseId && r.syndromeDefinitionId === syndromeId,
    );
  }
  if (!diseaseId) return false;
  return (props.displayRows ?? []).some(
    (r) => r.conditionDefinitionId === diseaseId || r.syndromeDefinitionId === diseaseId,
  );
}

/** 叶子节点：校验后 emit 由父级统一保存 */
function handleNodeClick(data: TreeNode) {
  if (data.opspRow) {
    handleOpspNodeClick(data.opspRow);
    return;
  }
  const src = data?.row;
  if (!src) return;
  if (isCommonRowAlreadySaved(src)) {
    ElMessage.info('该诊断已在当前列表中');
    return;
  }
  const syndromeId = String(src.syndromeDefinitionId ?? '').trim();
  emit('append', {
    conditionDefinitionId: src.conditionDefinitionId,
    syndromeDefinitionId: syndromeId || undefined,
    conditionCode: src.conditionCode ?? '',
    conditionDefinitionName: src.conditionDefinitionName ?? src.conditionName ?? '',
    medicineType: src.medicineType ?? src.diagnosisKind,
    diagnosisKind: src.diagnosisKind ?? src.medicineType,
    tcmSyndromeGroup: src.tcmSyndromeGroup ?? undefined,
  });
}

/** 慢病备案行：仅 matched 可导入 */
function handleOpspNodeClick(src: OpspFilingDiagnosisRowDto) {
  const status = String(src.matchStatus ?? '').trim();
  if (status === 'matched') {
    const defId = String(src.conditionDefinitionId ?? '').trim();
    if (!defId) {
      ElMessage.warning(src.matchHint || '该病种无法导入');
      return;
    }
    if (
      (props.displayRows ?? []).some((r) => String(r.conditionDefinitionId ?? '').trim() === defId)
    ) {
      ElMessage.info('该诊断已在当前列表中');
      return;
    }
    emit('append', {
      conditionDefinitionId: defId,
      conditionCode: src.conditionCode ?? '',
      conditionDefinitionName: src.conditionDefinitionName ?? src.opspDiseName ?? '',
      medicineType: src.diagnosisKind ?? undefined,
      diagnosisKind: src.diagnosisKind ?? undefined,
      // 慢病备案导入默认门诊慢病医疗类别，供后续开嘱/结算按慢病组使用
      medicalTypeCode: '140104',
    });
    return;
  }
  ElMessage.warning(src.matchHint || '该慢病备案不可导入');
}
</script>

<template>
  <aside class="quick-tree">
    <ElTree
      :data="quickTreeData"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
      @node-click="(data: TreeNode) => handleNodeClick(data)"
    />
  </aside>
</template>

<style scoped>
.quick-tree {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: auto;
}

.quick-tree :deep(.el-tree-node__content) {
  height: 32px;
}
</style>
