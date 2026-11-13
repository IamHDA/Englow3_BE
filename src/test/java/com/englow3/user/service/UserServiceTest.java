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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import com.englow3.user.service.impl.UserServiceImpl;

class UserServiceTest {
    private static final String BUCKET = "images";

    private final UserRepository userRepo = mock(UserRepository.class);

    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final ObjectStorageClient objectStorageClient = mock(ObjectStorageClient.class);

    private final UserService service = new UserServiceImpl(userRepo, currentUser, objectStorageClient, BUCKET);

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

    /** An image whose first bytes are those of the format it declares. */
    private MultipartFile mockImage(String contentType) {
        byte[] header = switch (contentType) {
            case "image/png" -> new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
            case "image/jpeg" -> new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };
            default -> new byte[] { 1, 2, 3 };
        };
        return mockImage(contentType, header);
    }

    private MultipartFile mockImage(String contentType, byte[] content) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn(1024L);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return file;
    }

    @Nested
    class Success {

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
        void wearsAPresetPictureWithoutUploadingAnything() {
            service.choosePresetAvatar("fox");

            verify(user).changeAvatar("preset:fox");
            verify(objectStorageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
        }

        /** A picture uploaded before presets existed is a file in the bucket; switching away removes it. */
        @Test
        void deletesAnUploadedPictureItReplaces() {
            when(user.getAvatarObjectKey()).thenReturn("users/old/avatar/previous.png");

            service.choosePresetAvatar("owl");

            verify(objectStorageClient).delete(BUCKET, "users/old/avatar/previous.png");
        }

        @Test
        void leavesTheBucketAloneWhenSwitchingBetweenPresets() {
            when(user.getAvatarObjectKey()).thenReturn("preset:fox");

            service.choosePresetAvatar("owl");

            verify(objectStorageClient, never()).delete(anyString(), anyString());
        }

        /** The change has happened by then; a stray file is no reason to report it as failed. */
        @Test
        void keepsTheChangeWhenTheOldPictureCannotBeDeleted() {
            when(user.getAvatarObjectKey()).thenReturn("users/old/avatar/previous.png");
            org.mockito.Mockito.doThrow(new RuntimeException("store down")).when(objectStorageClient)
                    .delete(anyString(), anyString());

            service.choosePresetAvatar("cat");

            verify(user).changeAvatar("preset:cat");
        }

        @Test
        void storesABannerUnderTheBannerPrefix() {
            service.changeBanner(mockImage("image/jpeg"));

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(objectStorageClient).upload(eq(BUCKET), key.capture(), any(), anyLong(), eq("image/jpeg"));
            assertThat(key.getValue()).startsWith("users/" + userId + "/banner/").endsWith(".jpg");
            verify(user).changeBanner(key.getValue());
        }

    }

    @Nested
    class Failure {

        @Test
        void failsWhenTheJwtPointsAtNoUserRow() {
            when(userRepo.findByAuthProviderId(any())).thenReturn(Optional.empty());

            assertThatThrownBy(service::me).isInstanceOf(NotFoundException.class)
                    .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("USER_NOT_FOUND");
        }

        /** Nothing a user sends becomes their face: only an id from the list is accepted. */
        @Test
        void refusesAPresetThatDoesNotExist() {
            assertThatThrownBy(() -> service.choosePresetAvatar("../../etc/passwd"))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("AVATAR_PRESET_UNKNOWN");
            verify(user, never()).changeAvatar(anyString());
        }

        @Test
        void refusesAContentTypeThatIsNotAnAllowedImage() {
            assertThatThrownBy(() -> service.changeBanner(mockImage("image/svg+xml")))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("IMAGE_TYPE_NOT_SUPPORTED");
            verify(objectStorageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
        }

        /** The bucket is public: a page or script labelled image/png would be published as one. */
        @Test
        void refusesAFileThatIsNotTheImageItClaimsToBe() {
            MultipartFile page = mockImage("image/png", "<html><script>".getBytes(StandardCharsets.US_ASCII));

            assertThatThrownBy(() -> service.changeBanner(page)).isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("IMAGE_CONTENT_MISMATCH");
            verify(objectStorageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
            verify(user, never()).changeBanner(anyString());
        }

        @Test
        void refusesAnImageOverFiveMegabytes() {
            MultipartFile huge = mockImage("image/png");
            when(huge.getSize()).thenReturn(6L * 1024 * 1024);

            assertThatThrownBy(() -> service.changeBanner(huge)).isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("IMAGE_TOO_LARGE");
            verify(objectStorageClient, never()).upload(anyString(), anyString(), any(), anyLong(), anyString());
        }

        @Test
        void refusesAnEmptyUpload() {
            MultipartFile empty = mock(MultipartFile.class);
            when(empty.isEmpty()).thenReturn(true);

            assertThatThrownBy(() -> service.changeBanner(empty)).isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("IMAGE_REQUIRED");
        }

    }

}
