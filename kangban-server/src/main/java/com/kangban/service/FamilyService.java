package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangban.common.BusinessException;
import com.kangban.common.Result;
import com.kangban.dto.request.AddFamilyRequest;
import com.kangban.dto.request.UpdateFamilyRequest;
import com.kangban.entity.FamilyMember;
import com.kangban.entity.HealthRecord;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.HealthRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyMemberMapper familyMemberMapper;
    private final HealthRecordMapper healthRecordMapper;
    private final MinioService minioService;

    public Result<List<Map<String, Object>>> list(Long userId) {
        List<FamilyMember> members = familyMemberMapper.selectList(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getUserId, userId)
                        .isNull(FamilyMember::getDeletedAt)
                        .orderByDesc(FamilyMember::getCreatedAt));
        List<Map<String, Object>> data = members.stream().map(this::toMap).toList();
        return Result.success(data);
    }

    public Result<Map<String, Object>> getById(Long userId, Long id) {
        FamilyMember member = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, id)
                        .eq(FamilyMember::getUserId, userId)
                        .isNull(FamilyMember::getDeletedAt));
        if (member == null) {
            throw BusinessException.notFound("家庭成员不存在");
        }
        return Result.success(toMap(member));
    }

    public Result<Map<String, Object>> add(Long userId, AddFamilyRequest request) {
        FamilyMember member = new FamilyMember();
        member.setUserId(userId);
        member.setName(request.getName());
        member.setRelation(request.getRelation());
        member.setAge(request.getAge());
        member.setGender(request.getGender());
        member.setNote(request.getNote());
        member.setCreatedAt(LocalDateTime.now());
        familyMemberMapper.insert(member);
        return Result.success("添加成功", toMap(member));
    }

    public void update(Long userId, Long id, UpdateFamilyRequest request) {
        FamilyMember member = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, id)
                        .eq(FamilyMember::getUserId, userId)
                        .isNull(FamilyMember::getDeletedAt));
        if (member == null) {
            throw BusinessException.notFound("家庭成员不存在");
        }
        if (request.getName() != null) {
            member.setName(request.getName());
        }
        if (request.getRelation() != null) {
            member.setRelation(request.getRelation());
        }
        if (request.getAge() != null) {
            member.setAge(request.getAge());
        }
        if (request.getGender() != null) {
            member.setGender(request.getGender());
        }
        if (request.getNote() != null) {
            member.setNote(request.getNote());
        }
        member.setUpdatedAt(LocalDateTime.now());
        familyMemberMapper.updateById(member);
    }

    public Map<String, Object> uploadAvatar(Long userId, Long id, MultipartFile file) {
        FamilyMember member = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, id)
                        .eq(FamilyMember::getUserId, userId)
                        .isNull(FamilyMember::getDeletedAt));
        if (member == null) {
            throw BusinessException.notFound("家庭成员不存在");
        }
        String avatarObject = minioService.uploadObject(file, userId);
        member.setAvatarUrl(avatarObject);
        member.setUpdatedAt(LocalDateTime.now());
        familyMemberMapper.updateById(member);

        Map<String, Object> result = new HashMap<>();
        result.put("url", minioService.getFileUrl(avatarObject));
        return result;
    }

    public void delete(Long userId, Long id) {
        FamilyMember member = familyMemberMapper.selectOne(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, id)
                        .eq(FamilyMember::getUserId, userId)
                        .isNull(FamilyMember::getDeletedAt));
        if (member == null) {
            throw BusinessException.notFound("家庭成员不存在");
        }
        Long recordCount = healthRecordMapper.selectCount(new LambdaQueryWrapper<HealthRecord>()
                .eq(HealthRecord::getUserId, userId)
                .eq(HealthRecord::getMemberId, id)
                .isNull(HealthRecord::getDeletedAt));
        if (recordCount != null && recordCount > 0) {
            throw BusinessException.conflict("该成员已有健康记录，不能直接删除");
        }
        member.setDeletedAt(LocalDateTime.now());
        familyMemberMapper.updateById(member);
    }

    private Map<String, Object> toMap(FamilyMember member) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", member.getId());
        map.put("userId", member.getUserId());
        map.put("name", member.getName());
        map.put("relation", member.getRelation());
        map.put("age", member.getAge());
        map.put("gender", member.getGender());
        map.put("avatarUrl", minioService.resolveFileUrl(member.getAvatarUrl()));
        map.put("note", member.getNote());
        map.put("createdAt", member.getCreatedAt());
        HealthRecord latestRecord = healthRecordMapper.selectOne(new LambdaQueryWrapper<HealthRecord>()
                .eq(HealthRecord::getUserId, member.getUserId())
                .eq(HealthRecord::getMemberId, member.getId())
                .isNull(HealthRecord::getDeletedAt)
                .orderByDesc(HealthRecord::getRecordedDate)
                .orderByDesc(HealthRecord::getRecordedTime)
                .last("LIMIT 1"));
        if (latestRecord != null) {
            Map<String, Object> latestHealth = new HashMap<>();
            latestHealth.put("metric", latestRecord.getMetric());
            latestHealth.put("value", latestRecord.getValue());
            latestHealth.put("unit", latestRecord.getUnit());
            latestHealth.put("recordedDate", latestRecord.getRecordedDate());
            latestHealth.put("recordedTime", latestRecord.getRecordedTime());
            map.put("latestHealth", latestHealth);
        }
        return map;
    }
}
