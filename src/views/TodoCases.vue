<template>
  <div class="page">
    <div class="page-title-row">
      <h2 class="page-title">待办案件</h2>
      <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openCreate">新建案件</el-button>
    </div>

    <SectionCard title="案件列表">
      <div class="filters">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索案件名称或编号"
          :prefix-icon="Search"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable @change="search">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="filters.caseType" placeholder="案件类型" clearable @keyup.enter="search" @clear="search" />
        <el-checkbox v-model="filters.overdue" @change="search">只看超期</el-checkbox>
        <el-button :icon="Search" @click="search">查询</el-button>
      </div>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

      <el-table v-loading="loading" :data="pageData.items" stripe empty-text="暂无可访问案件">
        <el-table-column prop="caseName" label="案件名称" min-width="230" show-overflow-tooltip />
        <el-table-column prop="caseNo" label="案件编号" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.caseNo || '未编号' }}</template>
        </el-table-column>
        <el-table-column prop="caseType" label="案件类型" width="110" />
        <el-table-column prop="handlerName" label="承办人" width="110">
          <template #default="{ row }">{{ row.handlerName || '待分配' }}</template>
        </el-table-column>
        <el-table-column prop="currentStage" label="当前环节" min-width="140">
          <template #default="{ row }">{{ row.currentStage || '未设置' }}</template>
        </el-table-column>
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">
            <span :class="{ overdue: row.overdue }">{{ formatDateTime(row.deadlineAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.overdue ? 'danger' : statusType[row.caseStatus]">{{ statusLabel[row.caseStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.caseId)">查看</el-button>
            <el-button v-if="canWrite" type="primary" link @click="openEdit(row.caseId)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="pageData.totalItems"
          @current-change="loadCases"
          @size-change="changePageSize"
        />
      </div>
    </SectionCard>

    <el-dialog v-model="formVisible" :title="editingCaseId ? '编辑案件' : '新建案件'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="案件名称" prop="caseName">
          <el-input v-model="form.caseName" maxlength="255" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="案件编号" prop="caseNo"><el-input v-model="form.caseNo" maxlength="100" /></el-form-item>
          <el-form-item label="案件类型" prop="caseType"><el-input v-model="form.caseType" maxlength="50" /></el-form-item>
          <el-form-item label="案由" prop="caseCause"><el-input v-model="form.caseCause" maxlength="255" /></el-form-item>
          <el-form-item label="案件来源" prop="caseSource"><el-input v-model="form.caseSource" maxlength="100" /></el-form-item>
          <el-form-item label="当前环节" prop="currentStage"><el-input v-model="form.currentStage" maxlength="50" /></el-form-item>
          <el-form-item label="受理日期" prop="acceptDate">
            <el-date-picker v-model="form.acceptDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
          <el-form-item label="截止时间" prop="deadlineAt" class="full-row">
            <el-date-picker v-model="form.deadlineAt" type="datetime" placeholder="选择时间" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCase">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="案件详情" size="680px">
      <div v-loading="detailLoading" class="detail-content">
        <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" />
        <template v-if="currentCase">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="案件名称" :span="2">{{ currentCase.caseName }}</el-descriptions-item>
            <el-descriptions-item label="案件编号">{{ currentCase.caseNo || '未编号' }}</el-descriptions-item>
            <el-descriptions-item label="案件类型">{{ currentCase.caseType }}</el-descriptions-item>
            <el-descriptions-item label="案由">{{ currentCase.caseCause || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="案件来源">{{ currentCase.caseSource || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="当前环节">{{ currentCase.currentStage || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel[currentCase.caseStatus] }}</el-descriptions-item>
            <el-descriptions-item label="受理日期">{{ currentCase.acceptDate || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="截止时间">
              <span :class="{ overdue: currentCase.overdue }">{{ formatDateTime(currentCase.deadlineAt) }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="case-actions">
            <el-button :icon="Tickets" @click="openCapability('/document-entities')">实体识别</el-button>
            <el-button :icon="Aim" @click="openCapability('/legal-elements')">法律要素</el-button>
            <el-button :icon="Document" @click="openCapability('/summary')">案件摘要</el-button>
            <el-button :icon="DocumentChecked" @click="openCapability('/review-report')">审查报告</el-button>
            <el-button v-if="canRecommend" :icon="CollectionTag" @click="openCapability('/case-recommend')">典型案例</el-button>
          </div>

          <el-tabs class="detail-tabs">
            <el-tab-pane label="参与人">
              <el-table :data="parties" size="small" empty-text="暂无参与人">
                <el-table-column prop="partyName" label="名称" min-width="120" />
                <el-table-column label="角色" width="100"><template #default="{ row }">{{ partyRoleLabel[row.partyRole] }}</template></el-table-column>
                <el-table-column label="类型" width="90"><template #default="{ row }">{{ row.partyType === 'PERSON' ? '自然人' : '组织' }}</template></el-table-column>
                <el-table-column prop="identityNumberMasked" label="证件号" min-width="150">
                  <template #default="{ row }">{{ row.identityNumberMasked || '未录入' }}</template>
                </el-table-column>
              </el-table>
              <p class="security-note">身份证号录入将在加密和脱敏方案确认后开放。</p>
            </el-tab-pane>
            <el-tab-pane label="分配历史">
              <el-table :data="assignments" size="small" empty-text="暂无分配记录">
                <el-table-column prop="realName" label="人员" min-width="110" />
                <el-table-column label="职责" width="110"><template #default="{ row }">{{ assignmentRoleLabel[row.assignmentRole] }}</template></el-table-column>
                <el-table-column prop="accessLevel" label="访问级别" width="100" />
                <el-table-column label="分配时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.assignedAt) }}</template></el-table-column>
                <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.endedAt ? 'info' : 'success'">{{ row.endedAt ? '已结束' : '当前' }}</el-tag></template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { Aim, CollectionTag, Document, DocumentChecked, Plus, Search, Tickets } from '@element-plus/icons-vue'
import SectionCard from '../components/common/SectionCard.vue'
import { hasPermission } from '../auth/session'
import { createCase, getCase, listCaseAssignments, listCaseParties, listCases, updateCase } from '../api/cases'

const canWrite = hasPermission('CASE_WRITE')
const canRecommend = hasPermission('RECOMMENDATION_USE')
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const pageData = reactive({ items: [], totalItems: 0, totalPages: 0 })
const filters = reactive({ page: 1, size: 20, keyword: '', status: '', caseType: '', overdue: false })
const statusOptions = [
  { value: 'PENDING', label: '待处理' }, { value: 'PROCESSING', label: '办理中' },
  { value: 'CLOSED', label: '已办结' }, { value: 'ARCHIVED', label: '已归档' }
]
const statusLabel = { PENDING: '待处理', PROCESSING: '办理中', CLOSED: '已办结', ARCHIVED: '已归档' }
const statusType = { PENDING: 'warning', PROCESSING: 'primary', CLOSED: 'success', ARCHIVED: 'info' }
const partyRoleLabel = { SUSPECT: '嫌疑人', VICTIM: '被害人', WITNESS: '证人', OTHER: '其他' }
const assignmentRoleLabel = { PROSECUTOR: '检察官', ASSIGNEE: '承办人', REVIEWER: '复核人', COLLABORATOR: '协办人' }

const formVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const editingCaseId = ref(null)
const emptyForm = () => ({ caseName: '', caseNo: '', caseType: '', caseCause: '', caseSource: '', currentStage: '', acceptDate: '', deadlineAt: null })
const form = reactive(emptyForm())
const rules = {
  caseName: [{ required: true, message: '请输入案件名称', trigger: 'blur' }],
  caseType: [{ required: true, message: '请输入案件类型', trigger: 'blur' }]
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const currentCase = ref(null)
const parties = ref([])
const assignments = ref([])

async function loadCases() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listCases(filters)
    pageData.items = response.items
    pageData.totalItems = response.totalItems
    pageData.totalPages = response.totalPages
  } catch (error) {
    errorMessage.value = error.message
    pageData.items = []
    pageData.totalItems = 0
  } finally {
    loading.value = false
  }
}

function search() { filters.page = 1; loadCases() }
function changePageSize() { filters.page = 1; loadCases() }
function resetForm() { Object.assign(form, emptyForm()) }
function openCreate() { editingCaseId.value = null; resetForm(); formVisible.value = true }

async function openEdit(caseId) {
  try {
    const item = await getCase(caseId)
    editingCaseId.value = caseId
    Object.assign(form, item, { deadlineAt: item.deadlineAt ? new Date(item.deadlineAt) : null })
    formVisible.value = true
  } catch (error) { ElMessage.error(error.message) }
}

async function submitCase() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      caseName: form.caseName, caseNo: form.caseNo || null, caseType: form.caseType,
      caseCause: form.caseCause || null, caseSource: form.caseSource || null,
      currentStage: form.currentStage || null, acceptDate: form.acceptDate || null,
      deadlineAt: form.deadlineAt ? new Date(form.deadlineAt).toISOString() : null
    }
    if (editingCaseId.value) await updateCase(editingCaseId.value, payload)
    else await createCase(payload)
    ElMessage.success(editingCaseId.value ? '案件已更新' : '案件已创建')
    formVisible.value = false
    await loadCases()
  } catch (error) {
    if (error?.message) ElMessage.error(error.message)
  } finally { saving.value = false }
}

async function openDetail(caseId) {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  currentCase.value = null
  try {
    const [caseDetail, caseParties, caseAssignments] = await Promise.all([
      getCase(caseId), listCaseParties(caseId), listCaseAssignments(caseId)
    ])
    currentCase.value = caseDetail
    parties.value = caseParties
    assignments.value = caseAssignments
  } catch (error) { detailError.value = error.message }
  finally { detailLoading.value = false }
}

function openCapability(path) {
  detailVisible.value = false
  router.push({ path, query: { caseId: currentCase.value.caseId } })
}

function formatDateTime(value) {
  if (!value) return '未设置'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value))
}

loadCases()
</script>

<style scoped>
.filters { display: grid; grid-template-columns: minmax(260px, 1fr) 150px 150px auto auto; align-items: center; gap: 10px; margin-bottom: 16px; }
.pagination-row { display: flex; justify-content: flex-end; padding-top: 16px; }
.overdue { color: var(--danger); font-weight: 600; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 16px; }
.full-row { grid-column: 1 / -1; }
.form-grid :deep(.el-date-editor) { width: 100%; }
.detail-content { min-height: 260px; }
.detail-tabs { margin-top: 20px; }
.case-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
.security-note { margin: 12px 0 0; color: var(--text-secondary); font-size: 12px; }
</style>
