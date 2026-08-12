export async function waitForJob(load, options = {}) {
  const attempts = options.attempts ?? 30
  const interval = options.interval ?? 1500
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const job = await load()
    if (job.status === 'SUCCESS') return job
    if (job.status === 'FAILED') throw new Error(job.errorCode || '任务执行失败')
    await new Promise((resolve) => window.setTimeout(resolve, interval))
  }
  return null
}
