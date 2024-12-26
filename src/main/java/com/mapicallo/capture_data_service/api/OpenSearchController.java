package com.mapicallo.capture_data_service.api;

import com.mapicallo.capture_data_service.application.OpenSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/opensearch")
@Tag(name = "OpenSearch Operations", description = "Endpoints to interact with OpenSearch")
public class OpenSearchController {

    @Autowired
    private OpenSearchService openSearchService;

    private static final String UPLOAD_DIR = "C:/uploaded_files/";

    @Operation(summary = "Index a document in OpenSearch", description = "Indexes a JSON document in a specified OpenSearch index.")
    @PostMapping("/index")
    public ResponseEntity<String> indexDocument(
            @RequestParam String indexName,
            @RequestParam(required = false) String documentId,
            @RequestBody Map<String, Object> document) {
        try {
            String result = openSearchService.indexDocument(indexName, documentId, document);
            return ResponseEntity.ok("Document indexed successfully. Result: " + result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error indexing document: " + e.getMessage());
        }
    }

    @Operation(summary = "Process and index a file", description = "Reads a file uploaded previously and indexes its content into OpenSearch.")
    @PostMapping("/process-file")
    public ResponseEntity<String> processFile(
            @RequestParam String indexName) {
        try {
            // Verificar si hay archivos en el directorio
            File uploadDir = new File(UPLOAD_DIR);
            File[] files = uploadDir.listFiles();
            if (files == null || files.length == 0) {
                return ResponseEntity.status(400).body("No file found in upload directory.");
            }

            // Tomar el primer archivo encontrado para procesar (mejorar según necesidad)
            File file = files[0];

            // Procesar el archivo y enviar a OpenSearch
            if (file.getName().endsWith(".json")) {
                // Procesar archivo JSON
                Map<String, Object> jsonDocument = openSearchService.readJsonFile(file);
                openSearchService.indexDocument(indexName, null, jsonDocument);
            } else if (file.getName().endsWith(".csv")) {
                // Procesar archivo CSV
                openSearchService.processCsvFile(file, indexName);
            } else {
                return ResponseEntity.status(400).body("Unsupported file format. Only JSON and CSV are allowed.");
            }

            return ResponseEntity.ok("File processed and indexed successfully.");

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @Operation(summary = "List all indices with document counts", description = "Lists all indices in OpenSearch along with the number of documents in each index.")
    @GetMapping("/list-indices")
    public ResponseEntity<Map<String, Long>> listIndicesWithDocumentCount() {
        try {
            Map<String, Long> indices = openSearchService.listIndicesWithDocumentCount();
            return ResponseEntity.ok(indices);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(null);
        }
    }


    @Operation(summary = "Delete an index", description = "Deletes a specific index from OpenSearch.")
    @DeleteMapping("/delete-index")
    public ResponseEntity<String> deleteIndex(@RequestParam String indexName) {
        try {
            boolean isDeleted = openSearchService.deleteIndex(indexName);
            if (isDeleted) {
                return ResponseEntity.ok("Index '" + indexName + "' deleted successfully.");
            } else {
                return ResponseEntity.status(404).body("Index '" + indexName + "' not found.");
            }
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error deleting index: " + e.getMessage());
        }
    }


}
