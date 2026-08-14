package com.kangban.controller;

import com.kangban.common.PageResult;
import com.kangban.common.Result;
import com.kangban.service.MedicalRecordService;
import com.kangban.service.ShareRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "病历管理")
@RestController
@RequestMapping("/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;
    private final ShareRecordService shareRecordService;

    @Operation(summary = "分页获取病历列表")
    @GetMapping
    public PageResult<Map<String, Object>> list(@AuthenticationPrincipal UserDetails user,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(required = false) Long memberId) {
        Long userId = Long.parseLong(user.getUsername());
        return medicalRecordService.list(userId, page, pageSize, memberId);
    }

    @Operation(summary = "获取病历详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getById(@AuthenticationPrincipal UserDetails user,
                                               @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return medicalRecordService.getById(userId, id);
    }

    @Operation(summary = "上传病历文件")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@AuthenticationPrincipal UserDetails user,
                                              @RequestParam(required = false) Long memberId,
                                              @RequestParam("file") MultipartFile file) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(medicalRecordService.upload(userId, memberId, file));
    }

    @Operation(summary = "删除病历")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal UserDetails user,
                               @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        medicalRecordService.delete(userId, id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "获取OCR分析状态")
    @GetMapping("/{id}/analysis")
    public Result<Map<String, Object>> getAnalysisStatus(@AuthenticationPrincipal UserDetails user,
                                                         @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(medicalRecordService.getAnalysisStatus(userId, id));
    }

    @Operation(summary = "重建病历私有检索索引")
    @PostMapping("/{id}/private-reindex")
    public Result<Map<String, Object>> reindexPrivate(@AuthenticationPrincipal UserDetails user,
                                                      @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(medicalRecordService.reindexPrivate(userId, id));
    }

    @Operation(summary = "批量重建历史病历私有检索索引")
    @PostMapping("/private-reindex/batch")
    public Result<Map<String, Object>> reindexPrivateBatch(@AuthenticationPrincipal UserDetails user,
                                                            @RequestParam(required = false) Long memberId,
                                                            @RequestParam(required = false) Integer limit) {
        Long userId = Long.parseLong(user.getUsername());
        return Result.success(medicalRecordService.reindexPrivateBatch(userId, memberId, limit));
    }

    // ==================== 分享功能 ====================

    @Operation(summary = "生成分享链接")
    @PostMapping("/{id}/share")
    public Result<Map<String, Object>> share(@AuthenticationPrincipal UserDetails user,
                                             @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return shareRecordService.createShare(userId, id);
    }

    @Operation(summary = "获取分享状态")
    @GetMapping("/{id}/share-status")
    public Result<Map<String, Object>> getShareStatus(@AuthenticationPrincipal UserDetails user,
                                                      @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return shareRecordService.getShareStatus(userId, id);
    }

    @Operation(summary = "撤销分享")
    @DeleteMapping("/{id}/share")
    public Result<Void> revokeShare(@AuthenticationPrincipal UserDetails user,
                                    @PathVariable Long id) {
        Long userId = Long.parseLong(user.getUsername());
        return shareRecordService.revokeShare(userId, id);
    }

    // ==================== PDF导出 ====================

    @Operation(summary = "导出病历PDF")
    @GetMapping("/{id}/print")
    public void printPdf(@AuthenticationPrincipal UserDetails user,
                         @PathVariable Long id,
                         @RequestParam(defaultValue = "false") boolean includeAnalysis,
                         HttpServletResponse response) {
        Long userId = Long.parseLong(user.getUsername());
        medicalRecordService.printPdf(userId, id, includeAnalysis, response);
    }
}
