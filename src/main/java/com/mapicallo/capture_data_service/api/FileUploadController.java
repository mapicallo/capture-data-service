package com.mapicallo.capture_data_service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "File Management", description = "Endpoints for managing file uploads and processing")
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);
    private static final String UPLOAD_DIR = "C:/uploaded_files/";

    @Operation(summary = "Upload a file", description = "Allows users to upload a file (CSV, JSON, etc.) and stores it in a local directory.")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file) { // No usar @Parameter aquí

        logger.info("Received file: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            logger.warn("File is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty. Please select a valid file to upload.");
        }

        try {
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                logger.error("Could not create upload directory");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not create the upload directory.");
            }

            String filePath = UPLOAD_DIR + file.getOriginalFilename();
            file.transferTo(new File(filePath));
            logger.info("File uploaded to: {}", filePath);

            return ResponseEntity.status(HttpStatus.OK).body("File uploaded successfully to: " + filePath);
        } catch (IOException e) {
            logger.error("Error while uploading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error while uploading the file: " + e.getMessage());
        }
    }
}
