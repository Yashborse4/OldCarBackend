package com.carselling.oldcar.scheduler;

import com.carselling.oldcar.service.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Slf4j
public class JobExecutionService {

    private final JobExecutionRepository jobExecutionRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    @Value("${spring.mail.username}") // reusing the sender email as admin email for now, or we can add a property
    private String adminEmail;

    public JobExecutionService(JobExecutionRepository jobExecutionRepository,
            EmailService emailService,
            ObjectMapper objectMapper) {
        this.jobExecutionRepository = jobExecutionRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;

        // Configure RetryTemplate programmatically for 3 attempts with exponential
        // backoff
        RetryTemplate template = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        template.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L); // 1 second
        backOffPolicy.setMultiplier(2.0);
        template.setBackOffPolicy(backOffPolicy);

        this.retryTemplate = template;
    }

    /**
     * Executes a runnable task with observability (logging, tracking, alerting).
     *
     * @param jobName Name of the job
     * @param task    The task logic
     */
    public void execute(String jobName, Runnable task) {
        executeWithMetrics(jobName, () -> {
            task.run();
            return Collections.emptyMap();
        });
    }

    /**
     * Executes a supplier task that returns metadata metrics.
     *
     * @param jobName Name of the job
     * @param task    The task logic returning metrics map
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Ensure job record IS saved even if outer transaction
                                                           // exists (mostly relevant for independent jobs)
    public void executeWithMetrics(String jobName, Supplier<Map<String, Object>> task) {
        log.info("Starting job: {}", jobName);

        JobExecution execution = JobExecution.builder()
                .jobName(jobName)
                .startTime(LocalDateTime.now())
                .status(JobExecution.JobStatus.RUNNING)
                .build();

        execution = jobExecutionRepository.save(execution);

        try {
            // Execute the actual task with retry
            Map<String, Object> metrics = retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.warn("Retrying job {} (attempt {})", jobName, context.getRetryCount() + 1);
                }
                return task.get();
            });

            execution.setEndTime(LocalDateTime.now());
            execution.setStatus(JobExecution.JobStatus.SUCCESS);

            if (metrics != null && !metrics.isEmpty()) {
                try {
                    execution.setMetadata(objectMapper.writeValueAsString(metrics));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize metrics for job {}", jobName, e);
                    execution.setMetadata("{\"error\": \"serialization_failed\"}");
                }
            }

            jobExecutionRepository.save(execution);
            long durationMs = Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();
            log.info("Job {} completed successfully in {} ms", jobName, durationMs);

        } catch (Exception e) {
            log.error("Job {} failed", jobName, e);

            execution.setEndTime(LocalDateTime.now());
            execution.setStatus(JobExecution.JobStatus.FAILED);
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 2000) {
                errorMsg = errorMsg.substring(0, 2000) + "..."; // Truncate to fit column
            }
            execution.setErrorMessage(errorMsg);

            jobExecutionRepository.save(execution);

            // Alerting
            sendFailureAlert(jobName, execution.getId(), e);

            // Re-throw so that if it's called from a Scheduler, Spring knows it failed
            // (optional, but good practice)
            // But we don't want to crash the whole app execution if manual calls.
            // For now, we swallow exception for the caller unless they strictly need it.
            // In Scheduled context, throwing means the scheduler prints stack trace too.
        }
    }

    private void sendFailureAlert(String jobName, Long executionId, Exception e) {
        try {
            String subject = "CRITICAL: Job Failure Alert - " + jobName;
            String body = String.format("""
                    Job Execution Failed!

                    Job Name: %s
                    Execution ID: %d
                    Time: %s
                    Error: %s

                    Please check the system logs for full stack trace.
                    """, jobName, executionId, LocalDateTime.now(), e.getMessage());

            if (adminEmail != null && !adminEmail.isBlank()) {
                emailService.sendTextEmail(adminEmail, subject, body);
            } else {
                log.warn("Admin email not configured, skipping alert email for job {}", jobName);
            }
        } catch (Exception mailEx) {
            log.error("Failed to send alert email for job {}", jobName, mailEx);
        }
    }

    public JobExecution getLastSuccessfulJob(String jobName) {
        return jobExecutionRepository.findTopByJobNameAndStatusOrderByStartTimeDesc(jobName,
                JobExecution.JobStatus.SUCCESS);
    }
}
