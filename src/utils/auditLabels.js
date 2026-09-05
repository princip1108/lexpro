const subjects = {
  CASE: '案件', CASE_ASSIGNMENT: '案件分工', CASE_PARTY: '案件当事人',
  CASE_CARD: '案卡回填', CASE_CARD_FIELD: '案卡字段', CASE_REPORT: '审查报告',
  CASE_SUMMARY: '案例摘要', CASE_RECOMMENDATION: '典型案例推送',
  DOCUMENT_PARSE: '文书解析', DOCUMENT_PARSE_RESULT: '文书解析结果',
  DOSSIER_FILE: '卷宗文件', EVIDENCE_FILE: '卷宗文件', DOSSIER_FOLDER: '卷宗目录',
  DOSSIER_TAG: '卷宗标签', FILE_TAG: '卷宗标签', ENTITY_RECOGNITION: '实体识别',
  ENTITY_RESULT: '实体结果', LEGAL_ELEMENTS: '法律要素', LEGAL_ELEMENT_RESULT: '要素结果',
  KNOWLEDGE: '知识内容', KNOWLEDGE_CONTENT: '知识内容', REPORT_TEMPLATE: '报告模板',
  TYPICAL_CASE: '典型案例', TYPICAL_CASES: '典型案例', USER: '用户', APP_USER: '用户',
  WORK_TASK: '工作任务', MODEL_CONFIG: '模型配置', MCP_TOOL: 'MCP工具'
}
const actions = {CREATED:'创建',UPDATED:'修改',DELETED:'删除',RESTORED:'恢复',
  DOWNLOADED:'下载',UPLOADED:'上传',STARTED:'开始处理',SUCCEEDED:'处理成功',FAILED:'处理失败',
  CONFIRMED:'确认',EXPORTED:'导出',FINALIZED:'定稿',REVIEW_RETURNED:'退回修改',
  REVIEW_SUBMITTED:'提交审核',ANALYZED:'分析完成',ANALYSIS_FAILED:'分析失败',
  FAVORITED:'收藏',UNFAVORITED:'取消收藏',IMPORTED:'导入完成',IMPORT_FAILED:'导入失败',
  ACTIVATED:'启用',DISABLED:'停用',BOOTSTRAPPED:'初始化',PASSWORD_RESET:'重置密码',
  STATUS_CHANGED:'变更状态',CALLED:'调用',ENDED:'结束'}
const explicit = {LOGIN_SUCCEEDED:'登录成功',LOGIN_FAILED:'登录失败',LOGOUT:'退出登录',CASE_ASSIGNED:'分配案件'}
export function auditAction(code='') {
  if(explicit[code]) return explicit[code]
  for(const subject of Object.keys(subjects).sort((a,b)=>b.length-a.length)) {
    if(code.startsWith(subject+'_')) {
      const action=code.slice(subject.length+1)
      return actions[action] ? subjects[subject]+'：'+actions[action] : code
    }
  }
  return code
}
export function auditTarget(target='') {
  const [type,...rest]=target.split(' ')
  return [subjects[type]||({SUCCESS:'成功',FAILED:'失败'}[type])||type,...rest].join(' ')
}
const fields={caseId:'案件编号',userId:'用户编号',username:'账号',status:'状态',
  previousStatus:'原状态',newStatus:'新状态',fileName:'文件名',fileSize:'文件大小',
  versionNo:'版本',docId:'文书编号',dossierId:'卷宗编号',errorCode:'错误代码',
  reportId:'报告编号',format:'格式',count:'数量',requestId:'请求编号',roleId:'角色编号',
  organizationId:'组织编号',sourceCount:'来源数量',fieldCount:'字段数量',reason:'原因'}
export function auditDetail(value) {
  if(!value)return ''
  let data
  try{data=typeof value==='string'?JSON.parse(value):value}catch{return value}
  if(!data||typeof data!=='object')return String(data??'')
  return Object.entries(data).map(([key,item])=>`${fields[key]||key}：${typeof item==='object'?JSON.stringify(item):item}`).join('；')
}
