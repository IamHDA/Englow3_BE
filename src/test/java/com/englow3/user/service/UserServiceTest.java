package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.dto.command.UpdateUserBasicInfoCommand;
import com.englow3.user.dto.result.UserInformationResult;
import com.englow3.user.entity.Gender;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

class UserServiceTest {

    private static final String BUCKET = "images";

    private final UserRepository userRepo = mock(UserRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final ObjectStorageClient objectStorageClient = mock(ObjectStorageClient.class);

    private final UserService service = new UserService(userRepo, currentUser, objectStorageClient, BUCKET);

    private final UUID userId = UUID.randomUUID();
    private final User user = mock(User.class);

    @BeforeEach
    void authenticateAnExistingUser() {
        UUID authProviderId = UUID.randomUUID();
        when(currentUser.authProviderId()).thenReturn(authProviderId);
        when(userRepo.findByAuthProviderId(authProviderId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(userRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void failsWhenTheJwtPointsAtNoUserRow() {
        when(userRepo.findByAuthProviderId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(service::me).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void survivesAUserWhoNeverSetAGenderOrBirthDate() {
        when(user.getGender()).thenReturn(null);
        when(user.getBirthDate()).thenReturn(null);

        UserInformationResult result = service.me();

        assertThat(result.gender()).isNull();
        assertThat(result.birthDate()).isNull();
    }

    @Test
    void handsEveryBasicFieldToTheEntityWithoutGuessingWhatChanged() {
        LocalDate birthDate = LocalDate.of(2001, 3, 15);

        service.updateBasicInfo(new UpdateUserBasicInfoCommand("Nguyen Van A", "A", Gender.MALE, birthDate));

        verify(user).changeFullName("Nguyen Van A");
        verify(user).rename("A");
        verify(user).changeGender(Gender.MALE);
        verify(user).changeBirthDate(birthDate);
    }

    @Test
    void storesAnAvatarUnderAServerGeneratedKeyInTheDefaultBucket() {
        service.changeAvatar(mockImage("image/png"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objectStorageClient).upload(eq(BUCKET), key.capture(), any(), anyLong(), eq("image/png"));
        assertThat(key.getValue()).startsWith("users/" + userId + "/avatar/").endsWith(".png");
        verify(user).changeAvatar(key.getValue());
    }

    @Test
    void storesABannerUnderTheBannerPrefix() {
        service.changeBanner(mockImage("image/jpeg"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(objectStorageClient).upload(eq(BUCKET), key.capture(), any(), anyLong(), eq("image/jpeg"));
        assertThat(key.getValue()).startsWith("users/" + userId + "/banner/").endsWith(".jpg");
        verify(user).changeBanner(key.getValue());
    }

    @Test
    void refusesAContentTypeThatIsNotAnAllowedImage() {
        assertThatThrownBy(() -> service.changeAvatar(mockImage("image/svg+xml")))
                .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("IMAGE_TYPE_NOT_SUPPORTED");
        verify(objectStorageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    void refusesAnEmptyUpload() {
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> service.changeAvatar(empty)).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("IMAGE_REQUIRED");
    }

    private MultipartFile mockImage(String contentType) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn(1024L);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return file;
    }
}
