package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.entity.AuditLog;
import com.mindbridgex.assessment.repository.AuditLogRepository;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.util.RequestInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final int MAX_DETAILS = 2000;
    private final AuditLogRepository repository;

    /**
     * Records an action performed by the currently authenticated user (joins the caller's transaction).
     */
    @Transactional
    public void log(String action, String entityType, Object entityId, String details) {
        Long actorId = null;
        String actorName = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            actorId = user.getId();
            actorName = user.getUsername();
        }
        logAs(actorId, actorName, action, entityType, entityId, details);
    }

    /**
     * Records an action for an explicit actor (e.g. login events, scheduler jobs).
     */
    @Transactional
    public void logAs(Long actorId, String actorName, String action, String entityType, Object entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorName(actorName == null ? "system" : actorName);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setDetails(details == null ? null : (details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details));
        entry.setIpAddress(RequestInfo.currentIp());
        repository.save(entry);
    }

    public AuditService(final AuditLogRepository repository) {
        this.repository = repository;
    }
}
