<script setup lang="ts">
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type {
  AutoRollConfigFormModel,
  AutoRollConfigTableRow,
  AutoRollSelectedCatalogItem,
} from '../services/map-nurse-station-auto-roll';

import type { NurseStationPricingCatalogRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.types';

import { computed, reactive, ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { httpPageNurseStationPricingCatalog } from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.http';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { PRICING_PRODUCT_TABLE_SERVICE } from '../services/map-nurse-station-inpatient-billing';
import {
  AUTO_ROLL_BED_SERVICE_CATEGORY_CODE,
  buildAutoRollCatalogFromTableRow,
  buildAutoRollCatalogRowKey,
  buildAutoRollFormFromTableRow,
} from '../services/map-nurse-station-auto-roll';

defineOptions({
  name: 'InpatientBillingAutoRollConfigDialog',
});

const props = withDefaults(defineProps<Props>(), {
  canOperate: false,
  editRow: null,
  patient: null,
  saving: false,
  visible: false,
});

const emit = defineEmits<{
  closed: [];
  saved: [
    payload: {
      form: AutoRollConfigFormModel;
      selectedCatalog: AutoRollSelectedCatalogItem;
      mode: 'create' | 'edit';
      ruleId?: string;
    },
  ];
  'update:visible': [value: boolean];
}>();

interface Props {
  canOperate?: boolean;
  editRow?: AutoRollConfigTableRow | null;
  patient?: NurseStationModulePatient | null;
  saving?: boolean;
  visible?: boolean;
}

const CATALOG_PAGE_SIZE = 20;

const catalogKeyword = ref('');
const catalogPageNo = ref(1);
const catalogTotal = ref(0);
const catalogRows = ref<NurseStationPricingCatalogRowDto[]>([]);
const selectedCatalog = ref<AutoRollSelectedCatalogItem | null>(null);
const formModel = reactive<AutoRollConfigFormModel>({
  quantityValue: 1,
  remark: '',
});

const catalogRequest = useRequest(httpPageNurseStationPricingCatalog, { manual: true });
const authStore = useAuthStore();

const visibleModel = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

/** 弹窗是否为编辑模式。 */
const isEditMode = computed(() => props.editRow != null);

/** 弹窗标题文案。 */
const dialogTitle = computed(() => (isEditMode.value ? '编辑床位费配置' : '新增床位费配置'));

/** 当前选中的床位服务展示名。 */
const selectedCatalogName = computed(() => selectedCatalog.value?.catalog.name?.trim() || '—');

/** 当前选中的床位服务价格。 */
const selectedCatalogPrice = computed(() => {
  const raw = selectedCatalog.value?.catalog.unitPrice;
  if (raw === undefined || raw === null || raw === '') {
    return '—';
  }
  const num = Number(raw);
  if (!Number.isFinite(num)) {
    return '—';
  }
  return `￥${formatMoneyDisplay(num)}`;
});

/** 取当前登录机构，供选品目录查询。 */
function resolveOrganizationUnitId(): string | undefined {
  const orgRaw = authStore.getSelectedOrganization();
  return preserveApiIdForRequest(orgRaw) ?? undefined;
}

/** 重置弹窗表单与选品状态。 */
function resetDialogState() {
  catalogKeyword.value = '';
  catalogPageNo.value = 1;
  catalogTotal.value = 0;
  catalogRows.value = [];
  selectedCatalog.value = null;
  formModel.quantityValue = 1;
  formModel.remark = '';
}

/** 编辑模式下用表格行预填表单与选品。 */
function applyEditRow(row: AutoRollConfigTableRow) {
  const form = buildAutoRollFormFromTableRow(row);
  formModel.quantityValue = form.quantityValue;
  formModel.remark = form.remark;
  selectedCatalog.value = buildAutoRollCatalogFromTableRow(row);
}

/** 加载床位服务选品目录。 */
async function loadCatalog() {
  const patient = props.patient;
  const encounterId = patient ? preserveApiIdForRequest(patient.id) : undefined;
  const patientId = patient ? preserveApiIdForRequest(patient.patientId) : undefined;
  const res = await catalogRequest.run({
    encounterId,
    patientId,
    keyword: catalogKeyword.value.trim() || undefined,
    pageNo: catalogPageNo.value,
    pageSize: CATALOG_PAGE_SIZE,
    productTableNames: [PRICING_PRODUCT_TABLE_SERVICE],
    categoryCode: AUTO_ROLL_BED_SERVICE_CATEGORY_CODE,
    organizationUnitId: resolveOrganizationUnitId(),
  });
  const parsed = parsePagedRowsFromApiData<NurseStationPricingCatalogRowDto>(res);
  catalogRows.value = parsed.content;
  catalogTotal.value = parsed.totalCount;
}

/** 弹窗打开时初始化并加载选品。 */
async function handleDialogOpen() {
  resetDialogState();
  if (props.editRow) {
    applyEditRow(props.editRow);
  }
  if (!props.canOperate) {
    return;
  }
  await loadCatalog();
}

/** 点击目录行选中床位服务。 */
function handleCatalogRowClick(row: NurseStationPricingCatalogRowDto) {
  const rowKey = buildAutoRollCatalogRowKey(row);
  selectedCatalog.value = { rowKey, catalog: row };
}

/** 校验并提交表单。 */
function handleSubmit() {
  if (!props.canOperate) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  if (!selectedCatalog.value) {
    ElMessage.warning('请先在左侧选择床位服务项目');
    return;
  }
  const activityId = preserveApiIdForRequest(
    selectedCatalog.value.catalog.activityId ?? selectedCatalog.value.catalog.productId,
  );
  const chargeDefinitionId = preserveApiIdForRequest(
    selectedCatalog.value.catalog.chargeItemDefinitionId,
  );
  if (!activityId || !chargeDefinitionId) {
    ElMessage.warning('所选床位服务项目缺少活动或定价信息，请重新选择');
    return;
  }
  if (!formModel.quantityValue || formModel.quantityValue <= 0) {
    ElMessage.warning('数量必须大于 0');
    return;
  }
  emit('saved', {
    form: {
      quantityValue: formModel.quantityValue,
      remark: formModel.remark.trim(),
    },
    selectedCatalog: selectedCatalog.value,
    mode: isEditMode.value ? 'edit' : 'create',
    ruleId: props.editRow?.ruleId,
  });
}

/** 目录翻页变更。 */
function handleCatalogPageChange(page: number) {
  catalogPageNo.value = page;
  void loadCatalog();
}

/** 目录关键字查询。 */
function handleCatalogSearch() {
  catalogPageNo.value = 1;
  void loadCatalog();
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      void handleDialogOpen();
    }
  },
);
</script>

<template>
  <AppDialog
    v-model="visibleModel"
    class="auto-roll-config-dialog-wrapper"
    :title="dialogTitle"
    width="980px"
    @closed="emit('closed')"
  >
    <div class="auto-roll-config-dialog">
      <ElAlert
        v-if="!canOperate"
        title="当前不是单选患者状态，请先在左侧只勾选 1 位患者"
        type="warning"
        :closable="false"
        class="w-full"
      />

      <div class="auto-roll-config-dialog__body">
        <section class="auto-roll-config-panel auto-roll-config-panel--left">
          <header class="auto-roll-config-panel__header">床位服务选品</header>
          <div class="auto-roll-config-filter">
            <ElInput
              v-model="catalogKeyword"
              clearable
              placeholder="编码/名称"
              class="auto-roll-config-filter__keyword"
              @keyup.enter="handleCatalogSearch"
            />
            <ElButton
              type="primary"
              :loading="catalogRequest.loading.value"
              @click="handleCatalogSearch"
            >
              查询
            </ElButton>
          </div>
          <ElTable
            v-loading="catalogRequest.loading.value"
            :data="catalogRows"
            border
            height="100%"
            highlight-current-row
            :row-key="(row: NurseStationPricingCatalogRowDto) => buildAutoRollCatalogRowKey(row)"
            :current-row-key="selectedCatalog?.rowKey"
            @row-click="handleCatalogRowClick"
          >
            <ElTableColumn prop="busNo" label="编码" min-width="92" />
            <ElTableColumn prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
            <ElTableColumn label="价格" min-width="88" align="right">
              <template #default="{ row }">
                {{
                  row.unitPrice != null && row.unitPrice !== ''
                    ? `￥${formatMoneyDisplay(Number(row.unitPrice))}`
                    : '—'
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="unitName" label="单位" width="60" />
          </ElTable>
          <div class="auto-roll-config-panel__pager">
            <ElPagination
              v-model:current-page="catalogPageNo"
              :page-size="CATALOG_PAGE_SIZE"
              :total="catalogTotal"
              layout="total, prev, pager, next"
              small
              @current-change="handleCatalogPageChange"
            />
          </div>
        </section>

        <section class="auto-roll-config-panel auto-roll-config-panel--right">
          <header class="auto-roll-config-panel__header">配置信息</header>
          <ElForm label-width="72px" class="auto-roll-config-form">
            <ElFormItem label="项目名称" required>
              <span class="auto-roll-config-form__readonly">{{ selectedCatalogName }}</span>
            </ElFormItem>
            <ElFormItem label="价格">
              <span class="auto-roll-config-form__readonly">{{ selectedCatalogPrice }}</span>
            </ElFormItem>
            <ElFormItem label="数量" required>
              <ElInputNumber
                v-model="formModel.quantityValue"
                :min="1"
                :step="1"
                :controls="false"
                class="auto-roll-config-form__qty"
              />
            </ElFormItem>
            <ElFormItem label="备注">
              <ElInput
                v-model="formModel.remark"
                type="textarea"
                :rows="3"
                maxlength="200"
                placeholder="可选"
              />
            </ElFormItem>
          </ElForm>
        </section>
      </div>
    </div>

    <template #footer>
      <ElButton @click="visibleModel = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" :disabled="!canOperate" @click="handleSubmit">
        保存
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.auto-roll-config-dialog-wrapper {
  max-width: calc(100vw - 48px);
}

.auto-roll-config-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.auto-roll-config-dialog__body {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(420px, 1fr) minmax(320px, 0.85fr);
  height: 420px;
}

.auto-roll-config-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.auto-roll-config-panel__header {
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  min-height: 42px;
  padding: 0 10px;
  display: flex;
  align-items: center;
}

.auto-roll-config-filter {
  align-items: center;
  display: flex;
  gap: 8px;
  min-height: 52px;
  padding: 8px 10px;
}

.auto-roll-config-filter__keyword {
  flex: 1;
}

.auto-roll-config-panel__pager {
  display: flex;
  justify-content: flex-end;
  padding: 8px 10px 0;
}

.auto-roll-config-form {
  padding: 12px 12px 0;
}

.auto-roll-config-form__readonly {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.auto-roll-config-form__qty {
  width: 120px;
}
</style>
