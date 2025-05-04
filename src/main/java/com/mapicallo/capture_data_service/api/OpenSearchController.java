package com.mapicallo.capture_data_service.api;

import com.mapicallo.capture_data_service.application.OpenAIClientService;
import com.mapicallo.capture_data_service.application.OpenSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/opensearch")
public class OpenSearchController {

    @Autowired
    private OpenSearchService openSearchService;

    @Autowired
    private OpenAIClientService openAIClientService;

    private static final String UPLOAD_DIR = "C:/uploaded_files/";

    // ----------- INDEX OPERATIONS ------------------
    @Tag(name = "Index Operations", description = "Endpoints to interact with Index")
    @Operation(summary = "Index a document in OpenSearch", description = "Indexes a JSON document in a specified OpenSearch index.")
    @PostMapping("/index")
    public ResponseEntity<String> indexDocument(
            @Parameter(description = "Index name (must be all lowercase letters, no special characters)")
            @RequestParam String indexName,
            @RequestParam(required = false) String documentId,
            @RequestBody Map<String, Object> document) {
        try {
            String result = openSearchService.indexDocument(indexName.toLowerCase(), documentId, document);
            return ResponseEntity.ok("Document indexed successfully. Result: " + result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error indexing document: " + e.getMessage());
        }
    }

    @Tag(name = "Index Operations")
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

    @Tag(name = "Index Operations")
    @Operation(summary = "Delete an index", description = "Deletes a specific index from OpenSearch.")
    @DeleteMapping("/delete-index")
    public ResponseEntity<String> deleteIndex(@Parameter(description = "Index name (must be all lowercase letters, no special characters)")
                                                  @RequestParam String indexName) {
        try {
            boolean isDeleted = openSearchService.deleteIndex(indexName.toLowerCase());
            if (isDeleted) {
                return ResponseEntity.ok("Index '" + indexName.toLowerCase() + "' deleted successfully.");
            } else {
                return ResponseEntity.status(404).body("Index '" + indexName.toLowerCase() + "' not found.");
            }
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error deleting index: " + e.getMessage());
        }
    }

    // ----------- DATA PROCESSING ------------------
    @Tag(name = "Data Processing", description = "Possible processing with the file")
    @Operation(summary = "Generic processing file", description = "The output corresponds to the mapping chosen for input data.")
    @PostMapping("/process-file")
    public ResponseEntity<String> processFile(@Parameter(description = "Index name (must be all lowercase letters, no special characters)")
                                                  @RequestParam String indexName) {
        try {
            File uploadDir = new File(UPLOAD_DIR);
            File[] files = uploadDir.listFiles();
            if (files == null || files.length == 0) {
                return ResponseEntity.status(400).body("No file found in upload directory.");
            }

            File file = files[0];

            if (file.getName().endsWith(".json")) {
                Map<String, Object> jsonDocument = openSearchService.readJsonFile(file);
                openSearchService.indexDocument(indexName.toLowerCase(), null, jsonDocument);
            } else if (file.getName().endsWith(".csv")) {
                openSearchService.processCsvFile(file, indexName.toLowerCase());
            } else {
                return ResponseEntity.status(400).body("Unsupported file format. Only JSON and CSV are allowed.");
            }

            return ResponseEntity.ok("File processed and indexed successfully.");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @Tag(name = "Data Processing")
    @Operation(summary = "PLN processing", description = "The output is the result of NLP processing semantic extraction of triples.")
    @PostMapping("/extract-triples")
    public ResponseEntity<String> extractTriples(@Parameter(description = "Index name (must be all lowercase letters, no special characters)")
                                                     @RequestParam String indexName) {
        // TODO: Implementar procesamiento NLP con tripletas
        return ResponseEntity.ok("Triples extracted and indexed (stub).");
    }

    @Tag(name = "Data Processing")
    @Operation(summary = "Big Data processing", description = "The output is the result of Big-Data processing, obtaining statistical information..")
    @PostMapping("/bigdata/summary")
    public ResponseEntity<String> summarizeBigData(@Parameter(description = "Index name (must be all lowercase letters, no special characters)")
                                                       @RequestParam String indexName) {
        // TODO: Implementar procesamiento Big Data
        return ResponseEntity.ok("Big Data summary computed and indexed (stub).");
    }

    @Tag(name = "Data Processing")
    @Operation(summary = "AI Data processing", description = "The output is the result of AI processing, obtaining a detailed summary of the indicated text.")
    @PostMapping("/ai/summarize")
    public ResponseEntity<String> summarizeAI(@Parameter(description = "Index name (must be all lowercase letters, no special characters)")
                                                  @RequestParam String indexName) {
        try {
            File uploadDir = new File(UPLOAD_DIR);
            File[] files = uploadDir.listFiles();
            if (files == null || files.length == 0) {
                return ResponseEntity.status(400).body("No file found in upload directory.");
            }

            File file = files[0];
            String content = openSearchService.readFileContentAsString(file);  // Método que te pasaré ahora

            // Llamada a OpenAI
            String summary = openAIClientService.summarizeText(content);

            // Documento resultante
            Map<String, Object> document = Map.of(
                    "note", content,
                    "summary", summary,
                    "source_file", file.getName(),
                    "timestamp", java.time.Instant.now().toString()
            );

            openSearchService.indexDocument(indexName.toLowerCase(), null, document);
            return ResponseEntity.ok("AI summary generated and indexed successfully.");

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error during AI processing: " + e.getMessage());
        }
    }


}
