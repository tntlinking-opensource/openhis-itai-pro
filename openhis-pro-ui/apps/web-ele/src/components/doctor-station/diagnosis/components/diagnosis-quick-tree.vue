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
  rows: EncounterDiagnosisRowDto[];
}>();

const emit = defineEmits<{
  append: [row: EncounterDiagnosisRowDto];
}>();

/** 四类常用数据挂树，仅叶子点击后向父级提交待并入行 */
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
      id: `${prefix}-${i}-${r.conditionDefinitionId ?? i}`,
      label: [r.conditionName, r.conditionCode].filter(Boolean).join(' '),
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

/** 叶子节点：校验后 emit 由父级统一保存 */
function handleNodeClick(data: TreeNode) {
  const src = data?.row;
  if (!src) return;
  const defId = src.conditionDefinitionId;
  if (props.rows.some((r) => r.conditionDefinitionId === defId)) {
    ElMessage.info('该诊断已在当前列表中');
    return;
  }
  emit('append', {
    conditionDefinitionId: src.conditionDefinitionId,
    conditionCode: src.conditionCode ?? '',
    conditionDefinitionName: src.conditionName ?? '',
    medicineType: src.medicineType,
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
