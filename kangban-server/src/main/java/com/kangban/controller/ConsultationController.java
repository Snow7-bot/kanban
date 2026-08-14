package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.dto.request.CreateSessionRequest;
import com.kangban.dto.request.SendMessageRequest;
import com.kangban.dto.request.UpdatePatientRequest;
import com.kangban.entity.ChatMessage;
import com.kangban.entity.ChatSession;
import com.kangban.service.ConsultationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Tag(name = "在线问诊")
@RestController
@RequestMapping("/consultation")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getSessions(@AuthenticationPrincipal UserDetails user,
                                                 @RequestParam(required = false) Long subjectUserId,
                                                 @RequestParam(required = false) Long memberId) {
        Long userId = Long.parseLong(user.getUsername());
        return consultationService.getSessions(userId, subjectUserId, memberId);
    }

    @Operation(summary = "创建新会话")
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@AuthenticationPrincipal UserDetails user,
                                             @Valid @RequestBody CreateSessionRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        return consultationService.createSession(userId, req);
    }

    @Operation(summary = "获取会话消息")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> getMessages(@AuthenticationPrincipal UserDetails user,
                                                 @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return consultationService.getMessages(userId, id);
    }

    @Operation(summary = "重新读取并输出当前患者健康概况")
    @PostMapping("/sessions/{id}/summary")
    public Result<ChatMessage> appendPatientSummary(@AuthenticationPrincipal UserDetails user,
                                                    @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return consultationService.appendPatientSummary(userId, id);
    }

    @Operation(summary = "发送消息")
    @PostMapping("/sessions/{id}/messages")
    public Result<Map<String, Object>> sendMessage(@AuthenticationPrincipal UserDetails user,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody SendMessageRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        return consultationService.sendMessage(userId, id, req);
    }

    @Operation(summary = "流式获取AI回复 (SSE)")
    @GetMapping(value = "/sessions/{id}/stream", produces = "text/event-stream")
    public SseEmitter streamResponse(@PathVariable Long id,
                                     @RequestParam Long messageId,
                                     @AuthenticationPrincipal UserDetails user) {
        Long userId = Long.parseLong(user.getUsername());
        return consultationService.streamAiResponse(userId, id, messageId);
    }

    @Operation(summary = "获取历史会话")
    @GetMapping("/history")
    public Result<List<ChatSession>> getHistory(@AuthenticationPrincipal UserDetails user) {
        Long userId = Long.parseLong(user.getUsername());
        return consultationService.getHistory(userId);
    }

    @Operation(summary = "更新患者信息")
    @PutMapping("/patient")
    public Result<Void> updatePatientProfile(@AuthenticationPrincipal UserDetails user,
                                             @Valid @RequestBody UpdatePatientRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        consultationService.updatePatientProfile(userId, req);
        return Result.success("更新成功", null);
    }
}
