package com.kangban.controller;

import com.kangban.common.PageResult;
import com.kangban.common.Result;
import com.kangban.dto.request.AddMedicationRequest;
import com.kangban.dto.request.CheckInteractionRequest;
import com.kangban.dto.request.UpdateMedicationRequest;
import com.kangban.service.MedicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用药管理")
@RestController
@RequestMapping("/medications")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @Operation(summary = "分页获取用药列表")
    @GetMapping
    public PageResult<Map<String, Object>> list(@AuthenticationPrincipal UserDetails user,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long memberId) {
        Long userId = Long.parseLong(user.getUsername());
        return medicationService.list(userId, page, pageSize, status, memberId);
    }

    @Operation(summary = "获取用药详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@AuthenticationPrincipal UserDetails user,
                                               @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return medicationService.getById(userId, id);
    }

    @Operation(summary = "添加用药提醒")
    @PostMapping
    public Result<Map<String, Object>> add(@AuthenticationPrincipal UserDetails user,
                                           @Valid @RequestBody AddMedicationRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        return medicationService.add(userId, req);
    }

    @Operation(summary = "更新用药提醒")
    @PutMapping("/{id}")
    public Result<Void> update(@AuthenticationPrincipal UserDetails user,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateMedicationRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        medicationService.update(userId, id, req);
        return Result.success("更新成功", null);
    }

    @Operation(summary = "删除用药提醒")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal UserDetails user,
                               @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        medicationService.delete(userId, id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "确认服药")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirmDose(@AuthenticationPrincipal UserDetails user,
                                    @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        medicationService.confirmDose(userId, id);
        return Result.success("已确认服药", null);
    }

    @Operation(summary = "检查药物相互作用")
    @PostMapping("/interaction")
    public Result<Map<String, Object>> checkInteraction(@AuthenticationPrincipal UserDetails user,
                                                        @Valid @RequestBody CheckInteractionRequest req) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(medicationService.checkInteraction(userId, req.getResolvedDrugIds()));
    }

    @Operation(summary = "获取服药记录")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(@AuthenticationPrincipal UserDetails user,
                                                        @RequestParam Long medicationId) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(medicationService.getHistory(userId, medicationId));
    }

    @Operation(summary = "搜索药品")
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchDrugs(@AuthenticationPrincipal UserDetails user,
                                                         @RequestParam String keyword) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(medicationService.searchDrugs(userId, keyword));
    }
}
