package com.lexpro.lexprobackend.processing.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

@Component
public class CaseSummaryScheduler {

    private final Executor executor;
    private final CaseSummaryWorker worker;

    public CaseSummaryScheduler(@Qualifier("documentProcessingExecutor") Executor executor,
                                CaseSummaryWorker worker) {
        this.executor = executor;
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void schedule(CaseSummaryRequestedEvent event) {
        try {
            executor.execute(() -> worker.process(event));
        } catch (TaskRejectedException exception) {
            worker.reject(event);
        }
    }
}
