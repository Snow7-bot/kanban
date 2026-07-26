package com.kangban.controller;

import com.kangban.common.Result;
import com.kangban.service.ShareRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "分享查看")
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareRecordService shareRecordService;

    @Operation(summary = "通过分享令牌查看病历（需登录）")
    @GetMapping("/{token}")
    public Result<Map<String, Object>> viewSharedRecord(@AuthenticationPrincipal UserDetails user,
                                                        @PathVariable String token) {
        Long userId = Long.parseLong(user.getUsername());
        return shareRecordService.getSharedRecord(userId, token);
    }
}
