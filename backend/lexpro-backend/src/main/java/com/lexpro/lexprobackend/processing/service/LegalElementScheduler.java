package com.lexpro.lexprobackend.processing.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

@Component
public class LegalElementScheduler {

    private final Executor executor;
    private final LegalElementWorker worker;

    public LegalElementScheduler(@Qualifier("documentProcessingExecutor") Executor executor,
                                 LegalElementWorker worker) {
        this.executor = executor;
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void schedule(LegalElementRequestedEvent event) {
        try {
            executor.execute(() -> worker.process(event));
        } catch (TaskRejectedException exception) {
            worker.reject(event);
        }
    }
}
