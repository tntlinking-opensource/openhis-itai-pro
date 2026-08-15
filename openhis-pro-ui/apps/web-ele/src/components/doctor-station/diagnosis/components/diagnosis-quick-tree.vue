<script setup lang="ts">
import type {
  CommonDiagnosesResponse,
  CommonDiagnosisRowDto,
  EncounterDiagnosisRowDto,
  OpspFilingDiagnosisRowDto,
  PatientOpspFilingDiagnosisResponse,
} from '#/api/doctor-station/diagnosis.types';

import { computed, ref, watch } from 'vue';

import { Minus } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElMessage, ElMessageBox, ElTree } from 'element-plus';

import { resolveOpspMedicalTypeCode } from '#/utils/chronic-medical-type';

interface TreeNode {
  id: string;
  label: string;
  /** 个人/科室叶子可解绑 */
  deletable?: boolean;
  row?: CommonDiagnosisRowDto;
  opspRow?: OpspFilingDiagnosisRowDto;
  children?: TreeNode[];
}

defineOptions({ name: 'DiagnosisQuickTree' });

const props = defineProps<{
  businessClass: CommonDiagnosesResponse | null;
  /** 当前就诊已保存诊断（中医病证组已合并，与右侧表格一致） */
  displayRows: EncounterDiagnosisRowDto[];
  /** 删除个人/科室常用进行中 */
  unbindLoading?: boolean;
  /** 慢特病备案；未启用时不渲染「慢病备案」分支 */
  opspFiling?: null | PatientOpspFilingDiagnosisResponse;
}>();

const emit = defineEmits<{
  append: [row: EncounterDiagnosisRowDto];
  /** 未匹配慢病备案：由父级补录 chronic_spec 后再导入 */
  ensureOpspAppend: [row: OpspFilingDiagnosisRowDto];
  /** 解绑个人/科室常用模板 */
  unbind: [templateId: string];
}>();

/** 正在确认删除的节点，防止连点 */
const pendingDeleteId = ref('');

/** 快捷树叶子仅展示名称（不拼接诊断编码） */
function formatDiagnosisLeafLabel(name?: null | string): string {
  return String(name ?? '').trim();
}

/**
 * 组装四类常用 + 可选慢病备案快捷树。
 * 个人/科室叶子带 templateId 时可删。
 */
const quickTreeData = computed<TreeNode[]>(() => {
  const b = props.businessClass;
  if (!b) return [];

  const mk = (
    prefix: string,
    label: string,
    list: CommonDiagnosisRowDto[] | null | undefined,
    deletable: boolean,
  ): TreeNode => ({
    id: prefix,
    label,
    children: (list ?? []).map((r, i) => {
      const templateId = String(r.templateId ?? '').trim();
      return {
        id: `${prefix}-${i}-${r.conditionDefinitionId ?? i}-${r.syndromeDefinitionId ?? ''}`,
        label: formatDiagnosisLeafLabel(r.conditionDefinitionName ?? r.conditionName),
        row: r,
        // 仅个人/科室且后端回填了模板主键时展示删除
        deletable: deletable && Boolean(templateId),
      };
    }),
  });

  const nodes: TreeNode[] = [
    mk('hist', '历史', b.patientHistoryList, false),
    mk('doc', '常用', b.doctorCommonUseList, false),
    mk('usr', '个人', b.userPersonalList, true),
    mk('org', '科室', b.organizationList, true),
  ];

  // CHS 未启用时整支隐藏（非灰显）
  if (props.opspFiling?.opspFilingImportEnabled) {
    const filingList = props.opspFiling.filingList ?? [];
    nodes.push({
      id: 'opsp',
      label: filingList.length > 0 ? `慢特病备案(${filingList.length})` : '慢特病备案',
      children: filingList.map((r, i) => ({
        id: `opsp-${i}-${r.opspDiseCode ?? i}`,
        label: formatOpspFilingLeafLabel(r),
        opspRow: r,
      })),
    });
  }

  return nodes;
});

/** 备案叶子：病种名 + 门诊慢病/门诊特病 */
function formatOpspFilingLeafLabel(r: OpspFilingDiagnosisRowDto): string {
  const name = formatDiagnosisLeafLabel(r.conditionDefinitionName ?? r.opspDiseName);
  const medLabel =
    r.medicalTypeCode === '140201'
      ? '门诊特病'
      : r.medicalTypeCode === '140104'
        ? '门诊慢病'
        : r.medicalTypeCode
          ? String(r.medicalTypeCode)
          : '';
  return medLabel ? `${name}（${medLabel}）` : name;
}

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

/** 慢病备案行：matched 直接导入；unmatched 交给父级补录后导入 */
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
    let medicalTypeCode: string;
    // 空标签默认门诊慢病 140104（与后端 5301 缺省一致）
    medicalTypeCode = resolveOpspMedicalTypeCode(src.medicalTypeCode);
    emit('append', {
      conditionDefinitionId: defId,
      conditionCode: src.conditionCode ?? '',
      conditionDefinitionName: src.conditionDefinitionName ?? src.opspDiseName ?? '',
      medicineType: src.diagnosisKind ?? undefined,
      diagnosisKind: src.diagnosisKind ?? undefined,
      medicalTypeCode,
    });
    return;
  }
  if (status === 'unmatched') {
    const code = String(src.opspDiseCode ?? '').trim();
    if (!code) {
      ElMessage.warning(src.matchHint || '该慢病备案不可导入');
      return;
    }
    emit('ensureOpspAppend', src);
    return;
  }
  ElMessage.warning(src.matchHint || '该慢病备案不可导入');
}

/** 删除个人/科室常用：确认后交给父级解绑并刷新树 */
async function handleDeleteClick(data: TreeNode, event: MouseEvent) {
  event.stopPropagation();
  const templateId = String(data.row?.templateId ?? '').trim();
  if (!templateId) {
    ElMessage.warning('缺少模板标识，无法删除');
    return;
  }
  const name = data.label || '该诊断';
  try {
    await ElMessageBox.confirm(`确认从常用中移除「${name}」？`, '删除常用诊断', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  pendingDeleteId.value = data.id;
  emit('unbind', templateId);
}

/** 解绑结束后清掉行内 loading 态 */
watch(
  () => props.unbindLoading,
  (loading, prev) => {
    if (prev && !loading) {
      pendingDeleteId.value = '';
    }
  },
);
</script>

<template>
  <aside class="quick-tree">
    <ElTree
      :data="quickTreeData"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
      @node-click="(data: TreeNode) => handleNodeClick(data)"
    >
      <template #default="{ data }">
        <div class="quick-tree__node">
          <span class="quick-tree__label" :title="data.label">{{ data.label }}</span>
          <ElButton
            v-if="data.deletable"
            class="quick-tree__delete"
            link
            size="small"
            :loading="unbindLoading && pendingDeleteId === data.id"
            title="移除常用"
            @click="(e: MouseEvent) => handleDeleteClick(data, e)"
          >
            <ElIcon :size="16"><Minus /></ElIcon>
          </ElButton>
        </div>
      </template>
    </ElTree>
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

.quick-tree__node {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-width: 0;
  padding-right: 4px;
}

.quick-tree__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-tree__delete {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0;
  color: inherit;
  opacity: 0.8;
}

.quick-tree__delete:hover {
  color: inherit;
}

.quick-tree__delete :deep(.el-icon) {
  font-weight: 700;
  color: inherit;
}

.quick-tree__node:hover .quick-tree__delete {
  opacity: 1;
}
</style>
