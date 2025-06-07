package com.mapicallo.capture_data_service.api;

import com.mapicallo.capture_data_service.application.OpenSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/opensearch")
public class OpenSearchController {

    @Autowired
    private OpenSearchService openSearchService;

    private static final String UPLOAD_DIR = "C:/uploaded_files/";

    // ----------- INDEX OPERATIONS ------------------
    @Tag(name = "Index Operations", description = "Endpoints to interact with Index")
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

    // ----------- DATA PROCESSING ------------------
    @Tag(name = "Data Processing", description = "Possible processing with the file")
    @Operation(summary = "Generic processing file", description = "The output corresponds to the mapping chosen for input data.")
    @PostMapping("/process-file")
    public ResponseEntity<String> processFile(@RequestParam String indexName) {
        try {
            File uploadDir = new File(UPLOAD_DIR);
            File[] files = uploadDir.listFiles();
            if (files == null || files.length == 0) {
                return ResponseEntity.status(400).body("No file found in upload directory.");
            }

            File file = files[0];

            if (file.getName().endsWith(".json")) {
                Map<String, Object> jsonDocument = openSearchService.readJsonFile(file);
                openSearchService.indexDocument(indexName, null, jsonDocument);
            } else if (file.getName().endsWith(".csv")) {
                openSearchService.processCsvFile(file, indexName);
            } else {
                return ResponseEntity.status(400).body("Unsupported file format. Only JSON and CSV are allowed.");
            }

            return ResponseEntity.ok("File processed and indexed successfully.");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error processing file: " + e.getMessage());
        }
    }

    @Tag(name = "Data Processing")
    @PostMapping("/extract-triples")
    @Operation(summary = "Extracción de relaciones semánticas", description = "Extrae triples (sujeto, relación, objeto) de una biografía previamente cargada en el sistema. Utiliza CoreNLP KBP.")
    public ResponseEntity<String> extractTriples(@RequestParam String fileName) {
        String resultJson = openSearchService.extractTriplesFromFile(fileName);
        return ResponseEntity.ok(resultJson);
    }


    @Tag(name = "Data Processing")
    @Operation(
            summary = "Big Data processing",
            description = "Realiza un análisis estadístico básico del archivo especificado (JSON o CSV) previamente cargado. Devuelve estadísticas como media, desviación estándar y conteo de registros numéricos por campo."
    )
    @PostMapping("/bigdata/summary")
    public ResponseEntity<String> summarizeBigData(@RequestParam String fileName) {
        try {
            String resultJson = openSearchService.summarizeBigDataFromFile(fileName);
            return ResponseEntity.ok(resultJson);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error procesando archivo: " + e.getMessage());
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "AI Data processing", description = "Summarizes a text file using basic AI techniques.")
    @PostMapping("/ai/summarize")
    public ResponseEntity<String> summarizeAI(@RequestParam String fileName) {
        try {
            String summary = openSearchService.summarizeTextFromFile(fileName);
            return ResponseEntity.ok(summary);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error procesando archivo: " + e.getMessage() + "\"}");
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Predict next value", description = "Calcula una predicción simple sobre una columna numérica del fichero CSV.")
    @PostMapping("/predict-trend")
    public ResponseEntity<Map<String, Double>> predictTrend(@RequestParam String fileName) {
        try {
            Map<String, Double> prediction = openSearchService.predictNextValueFromFile(fileName);
            return ResponseEntity.ok(prediction);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", -1.0));
        }
    }






    @Tag(name = "Data Processing")
    @Operation(summary = "Keyword extraction", description = "Extracts key terms from the input document to facilitate search and categorization.")
    @PostMapping("/keyword-extract")
    public ResponseEntity<String> extractKeywords(@RequestParam String fileName) {
        // TODO: Implementar extracción de keywords
        return ResponseEntity.ok("Keywords extracted (stub)");
    }

    @Tag(name = "Data Processing")
    @Operation(summary = "Text anonymization", description = "Removes or masks personal or sensitive information from the input text file.")
    @PostMapping("/anonymize-text")
    public ResponseEntity<String> anonymizeText(@RequestParam String fileName) {
        // TODO: Implementar anonimización de texto
        return ResponseEntity.ok("Text anonymized (stub)");
    }


    @Tag(name = "Data Processing")
    @Operation(summary = "Thematic clustering", description = "Groups similar entries or documents based on semantic similarity or shared features.")
    @PostMapping("/clustering")
    public ResponseEntity<String> clusterData(@RequestParam String fileName) {
        // TODO: Implementar clustering
        return ResponseEntity.ok("Data clustered (stub)");
    }


    @Tag(name = "Data Processing")
    @Operation(summary = "Sentiment analysis", description = "Performs a sentiment analysis (positive, neutral, negative) on the content of the uploaded file.")
    @PostMapping("/sentiment-analysis")
    public ResponseEntity<String> sentimentAnalysis(@RequestParam String fileName) {
        // TODO: Implementar análisis de sentimiento
        return ResponseEntity.ok("Sentiment analyzed (stub)");
    }


    @Tag(name = "Data Processing")
    @Operation(summary = "Entity recognition", description = "Identifies and classifies named entities (persons, organizations, locations, etc.) in the text.")
    @PostMapping("/entity-recognition")
    public ResponseEntity<String> recognizeEntities(@RequestParam String fileName) {
        // TODO: Implementar reconocimiento de entidades
        return ResponseEntity.ok("Entities recognized (stub)");
    }


    @Tag(name = "Data Processing")
    @Operation(summary = "Timeline builder", description = "Extracts and organizes chronological events from a text to build a timeline.")
    @PostMapping("/timeline-builder")
    public ResponseEntity<String> buildTimeline(@RequestParam String fileName) {
        // TODO: Implementar construcción de línea temporal
        return ResponseEntity.ok("Timeline built (stub)");
    }



}
