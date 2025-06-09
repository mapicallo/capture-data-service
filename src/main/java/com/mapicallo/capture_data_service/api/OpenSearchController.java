package com.mapicallo.capture_data_service.api;

import com.google.gson.Gson;
import com.mapicallo.capture_data_service.application.OpenSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/v1/opensearch")
public class OpenSearchController {

    @Autowired
    private OpenSearchService openSearchService;

    @Autowired
    private OpenSearchService.TextAnonymizerService textAnonymizerService;

    @Autowired
    private OpenSearchService.SentimentAnalysisService sentimentAnalysisService;


    @Autowired
    private OpenSearchService.TimelineBuilderService timelineBuilderService;

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
    @Operation(summary = "Generic file processing service", description = "Performs default processing on the uploaded file (placeholder endpoint for extensibility).")
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
    @Operation(summary = "ESemantic triple extraction service (subject–relation–object)", description = "Extracts structured knowledge in the form of triples (subject, relation, object) from natural language text.")
    public ResponseEntity<String> extractTriples(@RequestParam String fileName) {
        try {
            String json = openSearchService.extractTriplesFromFile(fileName);

            // Intentamos indexar, pero sin afectar al resultado del Swagger
            try {
                Gson gson = new Gson();
                Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> triples = gson.fromJson(json, type);

                String indexName = "result-extract-triples-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                for (Map<String, Object> triple : triples) {
                    openSearchService.indexGeneric(indexName, triple);
                }
            } catch (Exception indexException) {
                System.err.println("❌ [OpenSearch] No se pudo indexar: " + indexException.getMessage());
                // Aquí también podrías loguear con SLF4J o guardar en un log interno
            }

            return ResponseEntity.ok(json);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }


    @Tag(name = "Data Processing")
    @Operation(
            summary = "Big data statistical summary service",
            description = "Computes basic statistical metrics (mean, std. deviation, min, max) for numerical fields in CSV datasets."
    )
    @PostMapping("/bigdata/summary")
    public ResponseEntity<String> summarizeBigData(@RequestParam String fileName) {
        try {
            String summaryJson = openSearchService.summarizeBigDataFromFile(fileName);

            // Indexación resiliente
            try {
                Gson gson = new Gson();
                Type mapType = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();
                Map<String, Map<String, Double>> summaryMap = gson.fromJson(summaryJson, mapType);

                String indexName = "result-bigdata-summary-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                for (Map.Entry<String, Map<String, Double>> field : summaryMap.entrySet()) {
                    Map<String, Object> doc = new HashMap<>(field.getValue());
                    doc.put("field", field.getKey());
                    openSearchService.indexGeneric(indexName, doc);
                }
            } catch (Exception ex) {
                System.err.println("❌ [OpenSearch] No se pudo indexar el resumen de Big Data: " + ex.getMessage());
            }

            return ResponseEntity.ok(summaryJson);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Text summarization service", description = "Generates a concise summary from a JSON file containing medical or clinical descriptions.")
    @PostMapping("/ai/summarize")
    public ResponseEntity<String> summarizeAI(@RequestParam String fileName) {
        try {
            String summaryJson = openSearchService.summarizeTextFromFile(fileName);

            // Intentar indexar en OpenSearch
            try {
                Gson gson = new Gson();
                Map<String, Object> parsed = gson.fromJson(summaryJson, Map.class);
                List<String> summaryList = (List<String>) parsed.get("summary");

                String indexName = "result-ai-summarize-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                for (int i = 0; i < summaryList.size(); i++) {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("sentence", summaryList.get(i));
                    doc.put("position", i);
                    openSearchService.indexGeneric(indexName, doc);
                }
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] Fallo al indexar resumen AI: " + e.getMessage());
            }

            return ResponseEntity.ok(summaryJson);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Numerical trend prediction service", description = "Predicts the next value of a numerical series using linear regression from CSV files.")
    @PostMapping("/predict-trend")
    public ResponseEntity<Map<String, Double>> predictTrend(@RequestParam String fileName) {
        try {
            Map<String, Double> prediction = openSearchService.predictNextValueFromFile(fileName);

            // Intentar indexar en OpenSearch
            try {
                String indexName = "result-predict-trend-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                Map<String, Object> document = new HashMap<>();
                document.put("last_value", prediction.get("last_value"));
                document.put("predicted_value", prediction.get("predicted_value"));
                openSearchService.indexGeneric(indexName, document);
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] Error indexando predicción: " + e.getMessage());
            }

            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", -1.0));
        }
    }


    @Tag(name = "Data Processing")
    @PostMapping("/keyword-extract")
    @Operation(summary = "Keyword extraction service from text", description = "Extracts the most relevant keywords from input text based on term frequency filtering.")
    public ResponseEntity<Map<String, Object>> extractKeywords(@RequestParam String fileName) {
        try {
            String path = UPLOAD_DIR + fileName;
            String text = openSearchService.readTextFromFile(new File(path));
            List<String> keywords = openSearchService.extractKeywords(text);

            Map<String, Object> result = Map.of(
                    "file", fileName,
                    "keywords", keywords
            );

            // Intentar indexar en OpenSearch
            try {
                String indexName = "result-keyword-extract-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                openSearchService.indexGeneric(indexName, result);
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] Error indexando keywords: " + e.getMessage());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo procesar el archivo"));
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Text anonymization service", description = "Automatically removes or masks personal, clinical, or institutional identifiers from free-text documents.")
    @PostMapping("/anonymize-text")
    public ResponseEntity<Map<String, Object>> anonymizeText(@RequestParam String fileName) {
        try {
            String path = UPLOAD_DIR + fileName;
            String originalText = Files.readString(Path.of(path));
            String anonymized = openSearchService.new TextAnonymizerService().anonymizeTextFromFileContent(originalText);

            Map<String, Object> result = Map.of(
                    "file", fileName,
                    "anonymized_text", anonymized
            );

            // Intentar indexar el resultado
            try {
                String indexName = "result-anonymize-text-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                openSearchService.indexGeneric(indexName, result);
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] Error indexando resultado anonimizado: " + e.getMessage());
            }

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo leer o procesar el archivo"));
        }
    }


    @Tag(name = "Data Processing")
    @Operation(summary = "Thematic text clustering service", description = "Groups similar text entries into clusters based on shared vocabulary and term frequency (TF).")
    @PostMapping("/clustering")
    public ResponseEntity<Map<String, Object>> clusterData(@RequestParam String fileName) {
        try {
            Map<Integer, List<String>> clusters = openSearchService.clusterDocumentsFromFile(fileName);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file", fileName);
            result.put("clusters", clusters);

            // Intentar indexar cada cluster como documento separado
            try {
                String indexName = "result-clustering-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                for (Map.Entry<Integer, List<String>> entry : clusters.entrySet()) {
                    Map<String, Object> doc = Map.of(
                            "cluster_id", entry.getKey(),
                            "documents", entry.getValue()
                    );
                    openSearchService.indexGeneric(indexName, doc);
                }
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] Error al indexar clusters: " + e.getMessage());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    @Tag(name = "Data Processing")
    @Operation(summary = "Sentiment analysis service for clinical text",description = "Evaluates the sentiment of each sentence in a clinical report and computes an overall emotional tone.")
    @PostMapping("/sentiment-analysis")
    public ResponseEntity<Map<String, Object>> sentimentAnalysis(@RequestParam String fileName) {
        try {
            String filePath = "C:/uploaded_files/" + fileName;
            File file = new File(filePath);

            if (!file.exists()) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado"));
            }

            String content = Files.readString(file.toPath());
            Map<String, Object> analysis = openSearchService
                    .new SentimentAnalysisService()
                    .analyzeText(content);

            // Intentar indexar el resultado en OpenSearch
            try {
                String indexName = "result-sentiment-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                openSearchService.indexGeneric(indexName, analysis);
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] Error al indexar resultado de análisis de sentimiento: " + e.getMessage());
            }

            return ResponseEntity.ok(analysis);

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error leyendo el archivo: " + e.getMessage()));
        }
    }

    @Tag(name = "Data Processing")
    @Operation(summary = "Named entity recognition (NER) service",description = "Identifies and classifies named entities such as people, organizations, dates, or places in clinical text.")
    @PostMapping("/entity-recognition")
    public ResponseEntity<Map<String, List<String>>> recognizeEntities(@RequestParam String fileName) {
        try {
            String filePath = "C:/uploaded_files/" + fileName;
            File file = new File(filePath);

            if (!file.exists()) {
                return ResponseEntity.status(404).body(Map.of("error", List.of("Archivo no encontrado")));
            }

            Map<String, List<String>> entityMap = openSearchService.recognizeEntitiesFromFile(filePath);

            // Intentar indexar en OpenSearch con adaptación del tipo
            try {
                String indexName = "result-entities-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                Map<String, Object> wrapped = new HashMap<>();
                wrapped.put("entities", entityMap);
                openSearchService.indexGeneric(indexName, wrapped);
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] No se pudo indexar en OpenSearch: " + e.getMessage());
            }

            return ResponseEntity.ok(entityMap);

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", List.of("Error procesando archivo: " + e.getMessage())));
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Chronological event extraction and timeline builder service",description = "Identifies and organizes events mentioned in the text by date to build a clinical timeline.")
    @PostMapping("/timeline-builder")
    public ResponseEntity<Map<String, List<String>>> buildTimeline(@RequestParam String fileName) {
        try {
            String filePath = "C:/uploaded_files/" + fileName;
            File file = new File(filePath);

            if (!file.exists()) {
                return ResponseEntity.status(404).body(Map.of("error", List.of("Archivo no encontrado")));
            }

            Map<String, List<String>> timeline = openSearchService
                    .new TimelineBuilderService()
                    .buildTimeline(filePath);

            // Indexación en OpenSearch (envolver estructura)
            try {
                String indexName = "result-timeline-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                Map<String, Object> document = new HashMap<>();
                document.put("timeline", timeline);
                openSearchService.indexGeneric(indexName, document);
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] No se pudo indexar timeline: " + e.getMessage());
            }

            return ResponseEntity.ok(timeline);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", List.of("Error al construir timeline: " + e.getMessage())));
        }
    }



}
