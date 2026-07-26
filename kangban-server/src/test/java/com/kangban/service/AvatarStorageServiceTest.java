package com.kangban.service;

import com.kangban.entity.FamilyMember;
import com.kangban.entity.User;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.HealthRecordMapper;
import com.kangban.mapper.UserMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarStorageServiceTest {

    @Test
    void resolvesStoredObjectAndLegacySignedUrlToFreshUrl() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioService service = new MinioService(client, "kangban");
        when(client.getPresignedObjectUrl(any())).thenReturn("http://minio/fresh-url");

        assertThat(service.resolveFileUrl("9/avatar.jpg")).isEqualTo("http://minio/fresh-url");
        assertThat(service.resolveFileUrl("http://minio/kangban/9/avatar.jpg?old-signature"))
                .isEqualTo("http://minio/fresh-url");
    }

    @Test
    void userAvatarStoresObjectNameInsteadOfExpiringUrl() {
        UserMapper userMapper = mock(UserMapper.class);
        MinioService minioService = mock(MinioService.class);
        MultipartFile file = mock(MultipartFile.class);
        User user = new User();
        user.setId(9L);
        when(userMapper.selectById(9L)).thenReturn(user);
        when(minioService.uploadObject(file, 9L)).thenReturn("9/avatar-object");
        when(minioService.getFileUrl("9/avatar-object")).thenReturn("http://minio/fresh-url");

        Map<String, Object> result = new UserService(userMapper, minioService).uploadAvatar(9L, file);

        assertThat(user.getAvatarUrl()).isEqualTo("9/avatar-object");
        assertThat(result.get("url")).isEqualTo("http://minio/fresh-url");
        verify(userMapper).updateById(user);
    }

    @Test
    void familyAvatarStoresObjectNameForOwnedMember() {
        FamilyMemberMapper familyMapper = mock(FamilyMemberMapper.class);
        HealthRecordMapper healthMapper = mock(HealthRecordMapper.class);
        MinioService minioService = mock(MinioService.class);
        MultipartFile file = mock(MultipartFile.class);
        FamilyMember member = new FamilyMember();
        member.setId(5L);
        member.setUserId(9L);
        when(familyMapper.selectOne(any())).thenReturn(member);
        when(minioService.uploadObject(file, 9L)).thenReturn("9/family-avatar-object");
        when(minioService.getFileUrl("9/family-avatar-object")).thenReturn("http://minio/family-fresh-url");

        FamilyService service = new FamilyService(familyMapper, healthMapper, minioService);
        Map<String, Object> result = service.uploadAvatar(9L, 5L, file);

        assertThat(member.getAvatarUrl()).isEqualTo("9/family-avatar-object");
        assertThat(result.get("url")).isEqualTo("http://minio/family-fresh-url");
        verify(familyMapper).updateById(member);
    }
}
