<template>
  <div class="page report-page">
    <div class="page-title-row"><h2 class="page-title">审查报告</h2><div class="toolbar"><el-button v-if="canGenerate" type="primary" :icon="Plus" :disabled="!caseId" @click="openGenerator">生成报告</el-button><el-button :icon="Download" :disabled="!detail" @click="download('DOCX')">DOCX</el-button><el-button :icon="Download" :disabled="!detail" @click="download('PDF')">PDF</el-button></div></div>
    <CaseContextBar v-model="caseId" @change="loadReports" />
    <SectionCard title="报告版本">
      <el-table :data="reports" highlight-current-row @current-change="openReport"><el-table-column prop="reportTitle" label="报告标题" min-width="240"/><el-table-column prop="reportType" label="类型" width="130"/><el-table-column prop="versionNo" label="版本" width="80"/><el-table-column label="状态" width="150"><template #default="{row}"><el-tag :type="statusType(row.reportStatus)">{{ statusLabel(row.reportStatus) }}</el-tag><el-tag v-if="row.current" class="current-tag" type="success" effect="plain">当前</el-tag></template></el-table-column><el-table-column label="更新时间" width="180"><template #default="{row}">{{ formatTime(row.updatedAt) }}</template></el-table-column></el-table>
      <el-empty v-if="!reports.length" description="当前案件暂无报告历史；生成能力可通过 Swagger 验证" />
    </SectionCard>
    <SectionCard v-if="detail" :title="detail.report.reportTitle">
      <template #extra><div class="toolbar"><el-tag v-if="!detail.report.current" type="info">历史版本只读</el-tag><el-button v-if="detail.report.current&&detail.report.reportStatus==='DRAFT'" @click="submit">提交复核</el-button><el-button v-if="detail.report.current&&detail.report.reportStatus==='REVIEWING'" @click="returnVisible=true">退回</el-button><el-button v-if="detail.report.current&&detail.report.reportStatus==='REVIEWING'" type="primary" @click="finalize">定稿</el-button></div></template>
      <el-timeline><el-timeline-item v-for="section in detail.content?.sections || []" :key="section.code" type="primary"><div class="report-section"><h3>{{ section.title }}</h3><p>{{ section.content }}</p></div></el-timeline-item></el-timeline>
    </SectionCard>
    <el-dialog v-model="returnVisible" title="退回报告" width="480px"><el-input v-model="returnReason" type="textarea" :rows="4" placeholder="填写退回原因"/><template #footer><el-button @click="returnVisible=false">取消</el-button><el-button type="primary" @click="returnToDraft">确认退回</el-button></template></el-dialog>
    <el-dialog v-model="generatorVisible" title="生成审查报告" width="560px"><el-form label-width="90px"><el-form-item label="报告类型" required><el-input v-model="generator.reportType" disabled /></el-form-item><el-form-item label="报告标题" required><el-input v-model="generator.reportTitle" maxlength="255" /></el-form-item><el-form-item label="启用模板" required><el-select v-model="generator.templateId" style="width:100%" @change="alignReportType"><el-option v-for="item in templates" :key="item.templateId" :value="item.templateId" :label="`${item.templateName}（${item.templateType} · V${item.versionNo}）`" /></el-select></el-form-item><el-form-item label="确认案卡"><el-select v-model="generator.cardFillTaskId" clearable style="width:100%"><el-option v-for="item in confirmedCards" :key="item.fillTaskId" :value="item.fillTaskId" :label="`案卡 #${item.fillTaskId} · ${formatTime(item.confirmedAt)}`" /></el-select></el-form-item></el-form><el-alert title="生成任务由后端异步执行；外部 AI 安全开关关闭时会返回明确原因。" type="info" show-icon :closable="false"/><template #footer><el-button @click="generatorVisible=false">取消</el-button><el-button type="primary" :loading="generating" @click="generateReport">开始生成</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Plus } from '@element-plus/icons-vue'
import CaseContextBar from '../components/common/CaseContextBar.vue'
import SectionCard from '../components/common/SectionCard.vue'
import { hasPermission } from '../auth/session'
import { exportReport, finalizeReport, getReport, getReportJob, listCaseCards, listReports, listReportTemplates, returnReport, startReport, submitReportReview } from '../api/reports'
import { saveBlob } from '../utils/download'
import { waitForJob } from '../utils/jobs'
const caseId=ref(null);const reports=ref([]);const detail=ref(null);const returnVisible=ref(false);const returnReason=ref('')
const canGenerate=hasPermission('REPORT_MANAGE')&&hasPermission('AI_EXECUTE');const generatorVisible=ref(false);const generating=ref(false);const templates=ref([]);const confirmedCards=ref([]);const generator=ref({reportType:'REVIEW_REPORT',reportTitle:'',templateId:null,cardFillTaskId:null})
async function loadReports(){detail.value=null;if(!caseId.value)return;try{reports.value=await listReports(caseId.value);if(reports.value.length)await openReport(reports.value.find((i)=>i.current)||reports.value[0])}catch(error){ElMessage.error(error.message)}}
async function openReport(row){if(!row)return;try{detail.value=await getReport(caseId.value,row.reportId)}catch(error){ElMessage.error(error.message)}}
async function transition(action,success){try{detail.value=await action();ElMessage.success(success);await loadReports()}catch(error){ElMessage.error(error.message)}}
const submit=()=>transition(()=>submitReportReview(caseId.value,detail.value.report.reportId,detail.value.report.lockVersion),'已提交复核')
const finalize=()=>transition(()=>finalizeReport(caseId.value,detail.value.report.reportId,detail.value.report.lockVersion),'报告已定稿')
async function returnToDraft(){if(!returnReason.value.trim())return ElMessage.warning('请填写退回原因');await transition(()=>returnReport(caseId.value,detail.value.report.reportId,detail.value.report.lockVersion,returnReason.value),'报告已退回草稿');returnVisible.value=false}
async function download(format){try{saveBlob(await exportReport(caseId.value,detail.value.report.reportId,format),`LexPro-report.${format.toLowerCase()}`)}catch(error){ElMessage.error(error.message)}}
async function openGenerator(){try{const [templateList,cards]=await Promise.all([listReportTemplates('ACTIVE'),listCaseCards(caseId.value)]);templates.value=templateList;confirmedCards.value=cards.filter((item)=>item.fillStatus==='CONFIRMED');const template=templateList[0];generator.value={reportType:template?.templateType||'',reportTitle:'审查报告',templateId:template?.templateId||null,cardFillTaskId:null};generatorVisible.value=true}catch(error){ElMessage.error(error.message)}}
function alignReportType(templateId){const template=templates.value.find((item)=>item.templateId===templateId);generator.value.reportType=template?.templateType||''}
async function generateReport(){const template=templates.value.find((item)=>item.templateId===generator.value.templateId);if(!template||!generator.value.reportTitle.trim())return ElMessage.warning('请填写报告标题并选择模板');generating.value=true;try{const created=await startReport(caseId.value,{...generator.value,reportType:template.templateType,evidence:[],legalElementResultIds:[],typicalCases:[]});generatorVisible.value=false;const completed=await waitForJob(()=>getReportJob(caseId.value,created.requestId));await loadReports();if(completed)ElMessage.success('报告生成完成');else ElMessage.info('报告仍在后台生成，请稍后刷新')}catch(error){ElMessage.error(error.message)}finally{generating.value=false}}
function statusLabel(v){return({GENERATING:'生成中',DRAFT:'草稿',REVIEWING:'复核中',FINAL:'已定稿',FAILED:'生成失败'})[v]||v}function statusType(v){return({DRAFT:'info',REVIEWING:'warning',FINAL:'success',FAILED:'danger'})[v]||'primary'}function formatTime(v){return v?new Date(v).toLocaleString('zh-CN'):'-'}
</script>
<style scoped>.report-page{display:grid;gap:16px}.current-tag{margin-left:6px}.report-section{padding:16px 20px;background:#f7f9fd;border-radius:6px}.report-section h3{margin:0 0 10px;font-size:16px}.report-section p{margin:0;white-space:pre-wrap;line-height:1.9;color:#52657a}</style>
