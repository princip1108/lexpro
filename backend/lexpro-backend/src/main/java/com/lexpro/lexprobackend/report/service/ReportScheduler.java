package com.lexpro.lexprobackend.report.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

@Component
public class ReportScheduler {

    private final Executor executor;
    private final ReportWorker worker;

    public ReportScheduler(@Qualifier("documentProcessingExecutor") Executor executor, ReportWorker worker) {
        this.executor = executor;
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void schedule(ReportRequestedEvent event) {
        try {
            executor.execute(() -> worker.process(event));
        } catch (TaskRejectedException exception) {
            worker.reject(event);
        }
    }
}
