package com.folo.upload;

import com.folo.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/uploads")
public class ProfileImageUploadController {

    private final ProfileImageUploadService profileImageUploadService;

    public ProfileImageUploadController(ProfileImageUploadService profileImageUploadService) {
        this.profileImageUploadService = profileImageUploadService;
    }

    @PostMapping("/profile-image")
    public ApiResponse<ProfileImageUploadResponse> uploadProfileImage(
            @RequestPart("file") MultipartFile file
    ) {
        StoredProfileImage storedProfileImage = profileImageUploadService.upload(file);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(storedProfileImage.path())
                .toUriString();

        return ApiResponse.success(
                new ProfileImageUploadResponse(
                        url,
                        storedProfileImage.path(),
                        storedProfileImage.fileName(),
                        storedProfileImage.contentType(),
                        storedProfileImage.size()
                ),
                "프로필 이미지가 업로드되었습니다."
        );
    }
}
