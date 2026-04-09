package com.minipay.mpps.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // This tells Spring to wrap any method that has the @Auditable annotation
    @Around("@annotation(auditable)")
    public Object logAuditActivity(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String action = auditable.action();
        String status = "SUCCESS";
        String details = null;

        try {
            // Convert the method arguments into a JSON string for the audit details
            Object[] args = joinPoint.getArgs();
            if (args.length > 0) {
                details = objectMapper.writeValueAsString(args[0]);
            }

            // PROCEED: This actually executes the target method (e.g., createTransaction)
            Object result = joinPoint.proceed();

            return result;

        } catch (Exception e) {
            status = "FAILED";
            throw e; // Rethrow the exception so the app behaves normally!
        } finally {
            // Write to the database AFTER the method finishes (success or fail)
            saveAuditLog(action, status, details);
        }
    }

    private void saveAuditLog(String action, String status, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .status(status)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
            log.info("Audit log saved - Action: {}, Status: {}", action, status);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }
}