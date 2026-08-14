package com.kangban.agent;

import java.util.Map;

/**
 * Agent 只能提出动作草案，不能在此阶段直接执行业务写入。
 */
public record ActionProposal(
        String id,
        String type,
        Status status,
        Map<String, Object> parameters
) {

    public enum Status {
        DRAFT,
        PENDING_CONFIRMATION,
        CONFIRMED,
        EXECUTING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        EXPIRED
    }
}
