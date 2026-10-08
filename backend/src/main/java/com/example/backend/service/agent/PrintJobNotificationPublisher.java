package com.example.backend.service.agent;

import java.time.Instant;
import java.util.UUID;

import com.example.backend.dto.agent.AgentJobAvailableNotification;
import com.example.backend.entity.PrintJobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PrintJobNotificationPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintJobNotificationPublisher.class);
    private static final String USER_DESTINATION = "/queue/jobs";

    private final SimpMessagingTemplate messagingTemplate;

    public PrintJobNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyJobAvailable(PrintJobEntity job) {
        String agentCode = job.getAgent().getAgentCode();
        AgentJobAvailableNotification notification = new AgentJobAvailableNotification(
                UUID.randomUUID(),
                "JOB_AVAILABLE",
                job.getId(),
                job.getPrinter().getId(),
                Instant.now());
        Runnable dispatch = () -> send(agentCode, notification);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void send(String agentCode, AgentJobAvailableNotification notification) {
        try {
            messagingTemplate.convertAndSendToUser(agentCode, USER_DESTINATION, notification);
        } catch (MessagingException | IllegalStateException exception) {
            LOGGER.warn(
                    "Could not publish job-available hint for job {}. Agent polling remains available.",
                    notification.jobId(),
                    exception);
        }
    }
}
