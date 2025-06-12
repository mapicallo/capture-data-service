package com.mapicallo.capture_data_service.api;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health Check", description = "Endpoints for health checking")
public class isAlive {

    @Operation(summary = "Check if the service is alive", description = "Returns 200 OK if the service is running")
    @GetMapping("/isAlive")
    public ResponseEntity<String> isAlive(){

        return ResponseEntity.status(HttpStatus.OK).body("Service is running");
    }

}
