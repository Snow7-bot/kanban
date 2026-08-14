package com.kangban.service;

import com.kangban.entity.AuditLog;
import com.kangban.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public void record(Long actorUserId, String action, String resourceType,
                       Long resourceId, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(actorUserId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
}
