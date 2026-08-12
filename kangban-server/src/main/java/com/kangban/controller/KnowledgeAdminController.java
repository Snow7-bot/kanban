package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.rag.KnowledgeDocumentService;
import com.kangban.rag.KnowledgeSearchService;
import com.kangban.rag.RagAdminGuard;
import com.kangban.rag.RagSearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "公共知识库管理")
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeAdminController {

    private final KnowledgeDocumentService documentService;
    private final KnowledgeSearchService searchService;
    private final RagAdminGuard adminGuard;

    @Operation(summary = "上传公共知识文档")
    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public Result<Map<String, Object>> upload(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sourceUrl,
            @RequestPart("file") MultipartFile file) {
        adminGuard.require(adminToken);
        Long userId = userId(user);
        return Result.success(documentService.upload(userId, title, source, sourceUrl, file));
    }

    @Operation(summary = "查询公共知识文档")
    @GetMapping("/documents")
    public Result<List<Map<String, Object>>> list(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @RequestParam(required = false) String status) {
        adminGuard.require(adminToken);
        userId(user);
        return Result.success(documentService.list(status));
    }

    @Operation(summary = "查询入库任务")
    @GetMapping("/jobs/{id}")
    public Result<Map<String, Object>> job(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @PathVariable Long id) {
        adminGuard.require(adminToken);
        userId(user);
        return Result.success(documentService.getJob(id));
    }

    @Operation(summary = "预览文档切片")
    @GetMapping("/documents/{id}/chunks")
    public Result<List<Map<String, Object>>> chunks(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @PathVariable Long id) {
        adminGuard.require(adminToken);
        userId(user);
        return Result.success(documentService.chunks(id));
    }

    @Operation(summary = "提交文档审核")
    @PostMapping("/documents/{id}/submit-review")
    public Result<Void> submitReview(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @PathVariable Long id) {
        adminGuard.require(adminToken);
        documentService.submitReview(userId(user), id);
        return Result.success("已提交审核", null);
    }

    @Operation(summary = "发布文档")
    @PostMapping("/documents/{id}/publish")
    public Result<Void> publish(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @PathVariable Long id,
            @RequestParam(required = false) String reviewNote) {
        adminGuard.require(adminToken);
        documentService.publish(userId(user), id, reviewNote);
        return Result.success("已发布", null);
    }

    @Operation(summary = "撤回文档")
    @PostMapping("/documents/{id}/revoke")
    public Result<Void> revoke(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        adminGuard.require(adminToken);
        documentService.revoke(userId(user), id, reason);
        return Result.success("已撤回", null);
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/documents/{id}")
    public Result<Void> remove(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @PathVariable Long id) {
        adminGuard.require(adminToken);
        documentService.remove(userId(user), id);
        return Result.success("已删除", null);
    }

    @Operation(summary = "重建文档索引")
    @PostMapping("/documents/{id}/reindex")
    public Result<Map<String, Object>> reindex(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @PathVariable Long id) {
        adminGuard.require(adminToken);
        Long jobId = documentService.reindex(userId(user), id);
        return Result.success(Map.of("jobId", jobId));
    }

    @Operation(summary = "管理调试检索")
    @GetMapping("/search")
    public Result<Map<String, Object>> search(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader(value = "X-Knowledge-Admin-Token", required = false) String adminToken,
            @RequestParam String q) {
        adminGuard.require(adminToken);
        userId(user);
        RagSearchResult result = searchService.search(q);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("context", result.context());
        response.put("hits", result.hits());
        response.put("citations", result.citations());
        return Result.success(response);
    }

    private Long userId(UserDetails user) {
        return Long.parseLong(user.getUsername());
    }
}
