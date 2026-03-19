package com.folo.upload;

record ProfileImageUploadResponse(
        String url,
        String path,
        String fileName,
        String contentType,
        long size
) {
}

record StoredProfileImage(
        String path,
        String fileName,
        String contentType,
        long size
) {
}
