<template>
  <div>
    <div class="toolbar">
      <input v-model="filters.keyword" class="grow" placeholder="搜索案件名称 / 案号 / 嫌疑人" @keydown.enter="search">
      <button class="btn ghost" type="button" @click="search">查询</button>
      <button v-if="canWrite" class="btn primary" style="margin-left:auto" type="button" @click="openCreate">+ 新建案件</button>
    </div>

    <div v-if="errorMessage" class="notice error">{{ errorMessage }}</div>
    <div v-loading="loading" class="card">
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>案件名称</th><th>案号</th><th>类型</th><th>嫌疑人</th><th>状态</th><th>卷宗</th><th>受理日期</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-if="!pageData.items.length"><td class="empty" colspan="8">暂无可访问案件</td></tr>
            <tr v-for="row in pageData.items" :key="row.caseId" class="case-row">
              <td><div class="cname">{{ row.caseName }}</div></td>
              <td><div class="cno">{{ row.caseNo || '' }}</div></td>
              <td><span class="tag">{{ row.caseType || '刑事' }}</span></td>
              <td>{{ row.suspectName || '-' }}</td>
              <td><span class="badge" :class="statusClass(row)">{{ row.overdue ? '已超期' : statusLabel[row.caseStatus] || row.caseStatus }}</span></td>
              <td><span class="muted">{{ row.dossierCount || 0 }} 份</span></td>
              <td>{{ row.acceptDate || '-' }}</td>
              <td><button class="btn ghost sm" type="button" @click="openDetail(row.caseId)">查看详情</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div class="pager">共 {{ pageData.totalItems }} 条<button class="btn ghost sm" :disabled="filters.page <= 1" type="button" @click="changePage(-1)">上一页</button><span>第 {{ filters.page }} / {{ Math.max(pageData.totalPages, 1) }} 页</span><button class="btn ghost sm" :disabled="filters.page >= pageData.totalPages" type="button" @click="changePage(1)">下一页</button></div>

    <div v-if="formVisible" class="overlay" @click.self="formVisible=false">
      <div class="modal">
        <div class="modal-header"><h3>{{ editingCaseId ? '编辑案件' : '新建案件' }}</h3><button class="close-x" type="button" @click="formVisible=false">×</button></div>
        <div class="modal-body"><div class="grid g2">
          <div class="field"><label>案件编号 *</label><input v-model="form.caseNo" maxlength="100" placeholder="如 检刑诉〔2026〕1号"></div>
          <div class="field"><label>案件名称 *</label><input v-model="form.caseName" maxlength="255" placeholder="如 张某某诈骗案"></div>
          <div class="field"><label>案件类型</label><select v-model="form.caseType"><option>刑事</option><option>民事</option><option>行政</option><option>公益诉讼</option></select></div>
          <div class="field"><label>嫌疑人 / 当事人</label><input v-model="form.suspectName" placeholder="姓名"></div>
          <div class="field"><label>受理日期</label><input v-model="form.acceptDate" type="date"></div>
          <div class="field"><label>审查期限</label><input v-model="form.deadlineAt" type="date"></div>
          <div class="field"><label>案件状态</label><select disabled><option>待审查</option></select></div>
        </div></div>
        <div class="modal-footer"><button class="btn ghost" type="button" @click="formVisible=false">取消</button><button class="btn primary" :disabled="saving" type="button" @click="submitCase">{{ saving ? '保存中…' : '保存' }}</button></div>
      </div>
    </div>

    <div v-if="detailVisible" class="overlay" @click.self="detailVisible=false">
      <div class="modal detail-modal">
        <div class="modal-header"><h3>案件详情</h3><button class="close-x" type="button" @click="detailVisible=false">×</button></div>
        <div v-loading="detailLoading" class="modal-body">
          <div v-if="detailError" class="notice error">{{ detailError }}</div>
          <template v-if="currentCase">
            <div class="case-head"><div><h2>{{ currentCase.caseName }}</h2><div class="sub">{{ currentCase.caseNo || '未编号' }} · {{ currentCase.caseType }}</div></div><span class="badge" :class="statusClass(currentCase)">{{ currentCase.overdue ? '已超期' : statusLabel[currentCase.caseStatus] }}</span></div>
            <div class="card"><div class="card-title"><span class="bar"></span>案件信息</div><div class="kv-grid">
              <div><div class="k">案件编号</div><div class="v">{{ currentCase.caseNo || '-' }}</div></div><div><div class="k">案件类型</div><div class="v">{{ currentCase.caseType || '-' }}</div></div><div><div class="k">案由</div><div class="v">{{ currentCase.caseCause || '-' }}</div></div><div><div class="k">案件来源</div><div class="v">{{ currentCase.caseSource || '-' }}</div></div><div><div class="k">当前环节</div><div class="v">{{ currentCase.currentStage || '-' }}</div></div><div><div class="k">截止时间</div><div class="v">{{ formatDateTime(currentCase.deadlineAt) }}</div></div>
            </div></div>
            <div class="case-actions"><button class="btn ghost sm" @click="openCapability('/document-entities')">实体识别</button><button class="btn ghost sm" @click="openCapability('/legal-elements')">法律要素</button><button class="btn ghost sm" @click="openCapability('/summary')">案件摘要</button><button class="btn ghost sm" @click="openCapability('/review-report')">审查报告</button><button v-if="canRecommend" class="btn ghost sm" @click="openCapability('/case-recommend')">典型案例</button></div>
            <div class="tabs"><button class="tab" :class="{active:detailTab==='parties'}" @click="detailTab='parties'">参与人</button><button class="tab" :class="{active:detailTab==='assignments'}" @click="detailTab='assignments'">分配历史</button></div>
            <template v-if="detailTab==='parties'">
              <div class="flex flex-end mb8"><button v-if="canWrite" class="btn primary sm" @click="openPartyCreate">新增参与人</button></div>
              <div class="card table-wrap"><table class="table"><thead><tr><th>名称</th><th>角色</th><th>类型</th><th>证件号</th><th>操作</th></tr></thead><tbody><tr v-if="!parties.length"><td colspan="5" class="empty">暂无参与人</td></tr><tr v-for="row in parties" :key="row.partyId"><td>{{ row.partyName }}</td><td>{{ partyRoleLabel[row.partyRole] }}</td><td>{{ row.partyType === 'PERSON' ? '自然人' : '组织' }}</td><td>{{ row.identityNumberMasked || '未录入' }}</td><td><button v-if="canWrite" class="btn ghost sm" @click="openPartyEdit(row)">编辑</button></td></tr></tbody></table></div>
            </template>
            <template v-else>
              <div class="flex flex-end mb8"><button v-if="canAssign" class="btn primary sm" @click="assignmentVisible=true">新增分配</button></div>
              <div class="card table-wrap"><table class="table"><thead><tr><th>人员</th><th>职责</th><th>访问级别</th><th>分配时间</th><th>状态</th></tr></thead><tbody><tr v-if="!assignments.length"><td colspan="5" class="empty">暂无分配记录</td></tr><tr v-for="row in assignments" :key="row.assignmentId"><td>{{ row.realName || row.username }}</td><td>{{ assignmentRoleLabel[row.assignmentRole] }}</td><td>{{ row.accessLevel }}</td><td>{{ formatDateTime(row.assignedAt) }}</td><td><span class="tag" :class="row.endedAt ? 'gray' : 'green'">{{ row.endedAt ? '已结束' : '当前' }}</span> <button v-if="canAssign&&!row.endedAt" class="btn ghost danger sm" @click="endAssignment(row)">结束</button></td></tr></tbody></table></div>
            </template>
          </template>
        </div>
      </div>
    </div>

    <div v-if="partyVisible" class="overlay"><div class="modal small-modal"><div class="modal-header"><h3>{{ partyForm.partyId ? '编辑参与人' : '新增参与人' }}</h3><button class="close-x" @click="partyVisible=false">×</button></div><div class="modal-body"><div class="field"><label>名称</label><input v-model="partyForm.partyName"></div><div class="field"><label>角色</label><select v-model="partyForm.partyRole"><option v-for="item in partyRoleOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></div><div class="field"><label>类型</label><select v-model="partyForm.partyType"><option value="PERSON">自然人</option><option value="ORGANIZATION">组织</option></select></div><div class="field"><label>性别</label><input v-model="partyForm.gender"></div><div class="field"><label>说明</label><textarea v-model="partyForm.description" rows="3"></textarea></div></div><div class="modal-footer"><button class="btn ghost" @click="partyVisible=false">取消</button><button class="btn primary" @click="saveParty">保存</button></div></div></div>
    <div v-if="assignmentVisible" class="overlay"><div class="modal small-modal"><div class="modal-header"><h3>新增案件分配</h3><button class="close-x" @click="assignmentVisible=false">×</button></div><div class="modal-body"><div class="field"><label>人员</label><select v-model="assignmentForm.userId"><option :value="null">请选择人员</option><option v-for="user in users" :key="user.userId" :value="user.userId">{{ user.realName || user.username }}</option></select></div><div class="field"><label>职责</label><select v-model="assignmentForm.assignmentRole"><option v-for="item in assignmentRoleOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></div><div class="field"><label>访问级别</label><select v-model="assignmentForm.accessLevel"><option value="VIEW">查看</option><option value="EDIT">编辑</option><option value="MANAGE">管理</option></select></div></div><div class="modal-footer"><button class="btn ghost" @click="assignmentVisible=false">取消</button><button class="btn primary" @click="saveAssignment">保存</button></div></div></div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { hasPermission } from '../auth/session'
import { createCase, createCaseAssignment, createCaseParty, endCaseAssignment, getCase, listCaseAssignments, listCaseParties, listCases, updateCase, updateCaseParty } from '../api/cases'
import { listUsers } from '../api/admin'

const router = useRouter()
const canWrite = hasPermission('CASE_WRITE'), canAssign = hasPermission('CASE_ASSIGN'), canRecommend = hasPermission('RECOMMENDATION_USE')
const loading = ref(false), errorMessage = ref(''), formVisible = ref(false), saving = ref(false), editingCaseId = ref(null)
const detailVisible = ref(false), detailLoading = ref(false), detailError = ref(''), detailTab = ref('parties'), currentCase = ref(null)
const parties = ref([]), assignments = ref([]), users = ref([]), partyVisible = ref(false), assignmentVisible = ref(false)
const pageData = reactive({ items: [], totalItems: 0, totalPages: 0 })
const filters = reactive({ page: 1, size: 20, keyword: '', status: '', caseType: '', overdue: false })
const emptyForm = () => ({ caseName: '', caseNo: '', caseType: '刑事', suspectName: '', caseCause: '', caseSource: '', currentStage: '', acceptDate: '', deadlineAt: '' })
const form = reactive(emptyForm())
const partyForm = reactive({ partyId: null, partyName: '', partyRole: 'OTHER', partyType: 'PERSON', identityType: '', gender: '', birthDate: null, description: '' })
const assignmentForm = reactive({ userId: null, assignmentRole: 'COLLABORATOR', accessLevel: 'VIEW' })
const statusOptions = [{ value: 'PENDING', label: '待处理' }, { value: 'PROCESSING', label: '办理中' }, { value: 'CLOSED', label: '已办结' }, { value: 'ARCHIVED', label: '已归档' }]
const statusLabel = { PENDING: '待处理', PROCESSING: '办理中', CLOSED: '已办结', ARCHIVED: '已归档' }
const partyRoleLabel = { SUSPECT: '嫌疑人', VICTIM: '被害人', WITNESS: '证人', OTHER: '其他' }
const assignmentRoleLabel = { PROSECUTOR: '检察官', ASSIGNEE: '承办人', REVIEWER: '复核人', COLLABORATOR: '协办人' }
const partyRoleOptions = [{ value: 'SUSPECT', label: '嫌疑人' }, { value: 'VICTIM', label: '被害人' }, { value: 'WITNESS', label: '证人' }, { value: 'OTHER', label: '其他' }]
const assignmentRoleOptions = [{ value: 'PROSECUTOR', label: '检察官' }, { value: 'ASSIGNEE', label: '承办人' }, { value: 'REVIEWER', label: '复核人' }, { value: 'COLLABORATOR', label: '协办人' }]

async function loadCases() { loading.value = true; errorMessage.value = ''; try { const response = await listCases(filters); Object.assign(pageData, response) } catch (error) { errorMessage.value = error.message; Object.assign(pageData, { items: [], totalItems: 0, totalPages: 0 }) } finally { loading.value = false } }
function search() { filters.page = 1; loadCases() }
function changePage(direction) { filters.page += direction; loadCases() }
function openCreate() { editingCaseId.value = null; Object.assign(form, emptyForm()); formVisible.value = true }
async function openEdit(caseId) { try { const item = await getCase(caseId); editingCaseId.value = caseId; Object.assign(form, item, { deadlineAt: item.deadlineAt ? toLocalInput(item.deadlineAt) : '' }); formVisible.value = true } catch (error) { ElMessage.error(error.message) } }
async function submitCase() { if (!form.caseNo.trim() || !form.caseName.trim()) return ElMessage.warning('案件编号和案件名称必填'); saving.value = true; try { const payload = { caseName: form.caseName.trim(), caseNo: form.caseNo.trim(), caseType: form.caseType, caseCause: form.caseCause || null, caseSource: form.caseSource || null, currentStage: form.currentStage || null, acceptDate: form.acceptDate || null, deadlineAt: form.deadlineAt ? new Date(`${form.deadlineAt}T23:59:59`).toISOString() : null }; if (editingCaseId.value) await updateCase(editingCaseId.value, payload); else { const created=await createCase(payload); if(form.suspectName?.trim()) await createCaseParty(created.caseId,{partyName:form.suspectName.trim(),partyRole:'SUSPECT',partyType:'PERSON',identityType:null,gender:null,birthDate:null,description:null}) } formVisible.value = false; ElMessage.success(editingCaseId.value ? '案件已更新' : '案件已创建'); await loadCases() } catch (error) { ElMessage.error(error.message) } finally { saving.value = false } }
async function openDetail(caseId) { detailVisible.value = true; detailLoading.value = true; detailError.value = ''; detailTab.value = 'parties'; try { const values = await Promise.all([getCase(caseId), listCaseParties(caseId), listCaseAssignments(caseId)]); [currentCase.value, parties.value, assignments.value] = values; if (canAssign) users.value = (await listUsers(1, 100)).items || [] } catch (error) { detailError.value = error.message } finally { detailLoading.value = false } }
function resetParty() { Object.assign(partyForm, { partyId: null, partyName: '', partyRole: 'OTHER', partyType: 'PERSON', identityType: '', gender: '', birthDate: null, description: '' }) }
function openPartyCreate() { resetParty(); partyVisible.value = true }
function openPartyEdit(row) { Object.assign(partyForm, row); partyVisible.value = true }
async function saveParty() { if (!partyForm.partyName.trim()) return ElMessage.warning('请输入参与人名称'); try { const payload = { partyName: partyForm.partyName.trim(), partyRole: partyForm.partyRole, partyType: partyForm.partyType, identityType: partyForm.identityType || null, gender: partyForm.gender || null, birthDate: partyForm.birthDate || null, description: partyForm.description || null }; if (partyForm.partyId) await updateCaseParty(currentCase.value.caseId, partyForm.partyId, payload); else await createCaseParty(currentCase.value.caseId, payload); partyVisible.value = false; parties.value = await listCaseParties(currentCase.value.caseId); ElMessage.success('参与人已保存') } catch (error) { ElMessage.error(error.message) } }
async function saveAssignment() { if (!assignmentForm.userId) return ElMessage.warning('请选择人员'); try { await createCaseAssignment(currentCase.value.caseId, assignmentForm); assignmentVisible.value = false; assignments.value = await listCaseAssignments(currentCase.value.caseId); ElMessage.success('分配已创建') } catch (error) { ElMessage.error(error.message) } }
async function endAssignment(row) { try { await ElMessageBox.confirm(`结束 ${row.realName || row.username} 的当前分配？`, '确认结束'); await endCaseAssignment(currentCase.value.caseId, row.assignmentId); assignments.value = await listCaseAssignments(currentCase.value.caseId) } catch (error) { if (error !== 'cancel') ElMessage.error(error.message) } }
function openCapability(path) { detailVisible.value = false; router.push({ path, query: { caseId: currentCase.value.caseId } }) }
function statusClass(row) { if (row.overdue) return 'b-red'; return { PENDING: 'b-gold', PROCESSING: 'b-blue', CLOSED: 'b-green', ARCHIVED: 'b-gray' }[row.caseStatus] || 'b-gray' }
function formatDateTime(value) { if (!value) return '未设置'; return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value)) }
function toLocalInput(value) { const date = new Date(value), offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 16) }

loadCases()
</script>

<style scoped>
.case-check{display:flex;align-items:center;gap:4px;color:var(--muted);font-size:13px;white-space:nowrap}.case-check input{accent-color:var(--blue)}.case-row td:last-child{white-space:nowrap}.case-row td:last-child .btn+.btn{margin-left:6px}.overdue{color:var(--red);font-weight:600}.notice{margin-bottom:12px;padding:10px 14px;border-radius:8px}.notice.error{color:var(--red);background:var(--red-bg)}.detail-modal{width:min(94vw,1000px)}.small-modal{width:min(92vw,520px)}.case-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:16px}.tabs button{font-family:inherit;background:none;border:0}.modal-body .card{box-shadow:none}.mb8{margin-bottom:8px}
</style>
