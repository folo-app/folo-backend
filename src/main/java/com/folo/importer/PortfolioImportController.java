package com.folo.importer;

import com.folo.common.api.ApiResponse;
import com.folo.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/portfolio")
public class PortfolioImportController {

    private final ImportService importService;

    public PortfolioImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import/csv")
    public ApiResponse<CsvImportResponse> importCsv(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "broker", required = false) String broker
    ) {
        return ApiResponse.success(importService.importCsv(SecurityUtils.currentUserId(), file, broker), "CSV 파싱이 완료되었습니다.");
    }

    @PostMapping({"/import/confirm", "/import/csv/confirm"})
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfirmImportResponse> confirmImport(@RequestBody ConfirmImportRequest request) {
        ConfirmImportResponse response = importService.confirmImport(SecurityUtils.currentUserId(), request);
        return ApiResponse.success(response, response.savedTrades() + "건의 거래 기록이 저장되었습니다.");
    }

    @PostMapping("/import/ocr")
    public ApiResponse<OcrImportResponse> importOcr(@RequestPart("image") MultipartFile image) {
        return ApiResponse.success(importService.importOcr(SecurityUtils.currentUserId(), image), "OCR 파싱이 완료되었습니다.");
    }
}
