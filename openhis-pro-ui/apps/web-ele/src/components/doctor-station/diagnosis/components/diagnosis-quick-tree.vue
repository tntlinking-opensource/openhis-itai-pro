<script setup lang="ts">
import type {
  CommonDiagnosesResponse,
  CommonDiagnosisRowDto,
  EncounterDiagnosisRowDto,
} from '#/api/doctor-station/diagnosis.types';

import { computed } from 'vue';

import { ElMessage } from 'element-plus';

interface TreeNode {
  id: string;
  label: string;
  row?: CommonDiagnosisRowDto;
  children?: TreeNode[];
}

defineOptions({ name: 'DiagnosisQuickTree' });

const props = defineProps<{
  businessClass: CommonDiagnosesResponse | null;
  /** 当前就诊已保存诊断（中医病证组已合并，与右侧表格一致） */
  displayRows: EncounterDiagnosisRowDto[];
}>();

const emit = defineEmits<{
  append: [row: EncounterDiagnosisRowDto];
}>();

/** 快捷树叶子仅展示名称（不拼接诊断编码） */
function formatDiagnosisLeafLabel(name?: null | string): string {
  return String(name ?? '').trim();
}

/** 四类常用诊断快捷树 */
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

  return [
    mk('hist', '历史', b.patientHistoryList),
    mk('doc', '常用', b.doctorCommonUseList),
    mk('usr', '个人', b.userPersonalList),
    mk('org', '科室', b.organizationList),
  ];
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
</style>
