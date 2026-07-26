package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.dto.request.AddHealthRecordRequest;
import com.kangban.dto.request.UpdateHealthRecordRequest;
import com.kangban.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "健康管理")
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @Operation(summary = "获取健康趋势")
    @GetMapping("/trends")
    public Result<Map<String, Object>> getTrends(@AuthenticationPrincipal UserDetails user,
                                                 @RequestParam(required = false) String metric,
                                                 @RequestParam(required = false) Integer days,
                                                 @RequestParam(required = false) Long memberId,
                                                 @RequestParam(required = false) String member) {
        Long userId = Long.parseLong(user.getUsername());
        return healthService.getTrends(userId, metric, days, memberId, member);
    }

    @Operation(summary = "添加健康记录")
    @PostMapping("/records")
    public Result<Map<String, Object>> addRecord(@AuthenticationPrincipal UserDetails user,
                                                 @Valid @RequestBody AddHealthRecordRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        return healthService.addRecord(userId, req);
    }

    @Operation(summary = "更新健康记录")
    @PutMapping("/records/{id}")
    public Result<Void> updateRecord(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable Long id,
                                     @Valid @RequestBody UpdateHealthRecordRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        healthService.updateRecord(userId, id, req);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除健康记录")
    @DeleteMapping("/records/{id}")
    public Result<Void> deleteRecord(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        healthService.deleteRecord(userId, id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "获取健康报告")
    @GetMapping("/report")
    public Result<Map<String, Object>> getReport(@AuthenticationPrincipal UserDetails user,
                                                 @RequestParam(required = false) String period,
                                                 @RequestParam(required = false) Long memberId,
                                                 @RequestParam(required = false) String member) {
        Long userId = Long.parseLong(user.getUsername());
        return healthService.getReport(userId, period, memberId, member);
    }

    @Operation(summary = "获取可用指标类型")
    @GetMapping("/metrics")
    public Result<List<Map<String, Object>>> getMetrics() {
        return Result.success(healthService.getMetrics());
    }
}
