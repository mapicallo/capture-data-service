package com.mapicallo.capture_data_service.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/opensearch")
public class OpenSearchController {

    @Autowired
    private OpenSearchService openSearchService;

    @Autowired
    private OpenSearchService.TextAnonymizerService textAnonymizerService;

    @Autowired
    private OpenSearchService.SentimentAnalysisService sentimentAnalysisService;

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
    @PostMapping("/keyword-extract")
    @Operation(summary = "Keyword extraction", description = "Extracts key terms from the input document to facilitate search and categorization.")
    public ResponseEntity<Map<String, Object>> extractKeywords(@RequestParam String fileName) {
        try {
            File file = new File(UPLOAD_DIR + fileName);
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File not found: " + fileName));
            }

            // Leer el texto del fichero
            String textContent = openSearchService.readTextFromFile(file);
            if (textContent == null || textContent.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Empty or invalid file."));
            }

            // Extraer keywords
            List<String> keywords = openSearchService.extractKeywords(textContent);

            return ResponseEntity.ok(Map.of(
                    "keywords", keywords,
                    "file", fileName,
                    "total_keywords", keywords.size()
            ));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Text anonymization", description = "Removes or masks personal or sensitive information from the input text file.")
    @PostMapping("/anonymize-text")
    public ResponseEntity<String> anonymizeText(@RequestParam String fileName) {
        try {
            File file = new File("C:/uploaded_files/" + fileName);
            if (!file.exists()) {
                return ResponseEntity.status(404).body("El fichero no existe.");
            }

            String content = Files.readString(file.toPath());
            String anonymized = textAnonymizerService.anonymizeTextFromFileContent(content);
            return ResponseEntity.ok(anonymized);

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error leyendo o procesando el fichero: " + e.getMessage());
        }
    }


    @Tag(name = "Data Processing")
    @Operation(summary = "Thematic clustering", description = "Groups similar entries or documents based on semantic similarity or shared features.")
    @PostMapping("/clustering")
    public ResponseEntity<String> clusterData(@RequestParam String fileName) {
        try {
            Map<Integer, List<String>> clusters = openSearchService.clusterDocumentsFromFile(fileName);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return ResponseEntity.ok(gson.toJson(clusters));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reading file: " + e.getMessage());
        }
    }


    @Tag(name = "Data Processing")
    @PostMapping("/sentiment-analysis")
    public ResponseEntity<Map<String, Object>> sentimentAnalysis(@RequestParam String fileName) {
        String path = UPLOAD_DIR + fileName;
        Map<String, Object> result = sentimentAnalysisService.analyzeText(path);
        return ResponseEntity.ok(result);
    }

    @Tag(name = "Data Processing")
    @PostMapping("/entity-recognition")
    public ResponseEntity<Map<String, List<String>>> recognizeEntities(@RequestParam String fileName) {
        String filePath = UPLOAD_DIR + fileName;
        try {
            Map<String, List<String>> entities = openSearchService.recognizeEntitiesFromFile(filePath);
            return ResponseEntity.ok(entities);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", List.of("Error processing file: " + e.getMessage())));
        }
    }





    @Tag(name = "Data Processing")
    @Operation(summary = "Timeline builder", description = "Extracts and organizes chronological events from a text to build a timeline.")
    @PostMapping("/timeline-builder")
    public ResponseEntity<String> buildTimeline(@RequestParam String fileName) {
        // TODO: Implementar construcción de línea temporal
        return ResponseEntity.ok("Timeline built (stub)");
    }



}
