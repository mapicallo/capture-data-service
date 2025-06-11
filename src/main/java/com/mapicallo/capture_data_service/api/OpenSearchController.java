package com.mapicallo.capture_data_service.api;

import com.google.gson.Gson;
import com.mapicallo.capture_data_service.application.OpenSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/opensearch")
public class OpenSearchController {

    @Autowired
    private OpenSearchService openSearchService;

    @Autowired
    private OpenSearchService.TextAnonymizerService textAnonymizerService;

    /*@Autowired
    private OpenSearchService.SentimentAnalysisService sentimentAnalysisService;*/


    /*@Autowired
    private OpenSearchService.TimelineBuilderService timelineBuilderService;*/

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
    public ResponseEntity<String> processFile(@RequestParam String fileName, @RequestParam String indexName) {
        try {
            File file = new File(UPLOAD_DIR + fileName);
            if (!file.exists()) {
                return ResponseEntity.status(404).body("Archivo no encontrado: " + fileName);
            }

            if (file.getName().endsWith(".json")) {
                Map<String, Object> jsonDocument = openSearchService.readJsonFile(file);
                try {
                    openSearchService.indexDocument(indexName, null, jsonDocument);
                } catch (Exception e) {
                    System.err.println("⚠️ [OpenSearch] Indexing failed (JSON): " + e.getMessage());
                }
            } else if (file.getName().endsWith(".csv")) {
                try {
                    openSearchService.processCsvFile(file, indexName);
                } catch (Exception e) {
                    System.err.println("⚠️ [OpenSearch] Indexing failed (CSV): " + e.getMessage());
                }
            } else {
                return ResponseEntity.status(400).body("Unsupported file format. Only JSON and CSV are allowed.");
            }

            return ResponseEntity.ok("File '" + fileName + "' processed successfully (indexing optional).");

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
                String timestamp = Instant.now().toString(); // Marca temporal común para todos los documentos

                for (Map.Entry<String, Map<String, Double>> field : summaryMap.entrySet()) {
                    Map<String, Object> doc = new HashMap<>(field.getValue());
                    doc.put("field", field.getKey());
                    doc.put("timestamp", timestamp);
                    doc.put("source_endpoint", "bigdata/summary");
                    doc.put("fileName", fileName);
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
    public ResponseEntity<Object> summarizeAI(@RequestParam String fileName) {
        try {
            Path filePath = Path.of(UPLOAD_DIR, fileName);
            String content = Files.readString(filePath);

            Gson gson = new Gson();
            Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> entries = gson.fromJson(content, listType);

            List<Map<String, Object>> indexedResults = new ArrayList<>();
            String indexName = "result-ai-summarize-" + fileName.replaceAll("\\W+", "-").toLowerCase();

            for (Map<String, Object> entry : entries) {
                String description = (String) entry.get("description");
                Map<String, Object> summaryResult = openSearchService.summarizeText(description);

                Map<String, Object> enriched = new HashMap<>();
                enriched.put("id", entry.get("id"));
                enriched.put("timestamp", entry.get("timestamp"));
                enriched.put("source_endpoint", entry.get("source_endpoint"));
                enriched.put("summary", summaryResult.get("summary"));
                enriched.put("original_length", summaryResult.get("original_length"));

                indexedResults.add(enriched);
            }

            // 🔒 Intentar indexar todo (resiliente)
            try {
                for (Map<String, Object> doc : indexedResults) {
                    openSearchService.indexGeneric(indexName, doc);
                }
            } catch (Exception e) {
                System.err.println("❌ [OpenSearch] No se pudo realizar la indexación masiva: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                    "fileName", fileName,
                    "indexedCount", indexedResults.size(),
                    "sample", indexedResults.subList(0, Math.min(3, indexedResults.size()))
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Numerical trend prediction service", description = "Predicts the next value of a numerical series using linear regression from CSV files.")
    @PostMapping("/predict-trend")
    public ResponseEntity<Map<String, Object>> predictTrend(@RequestParam String fileName) {
        try {
            Map<String, Object> prediction = openSearchService.predictNextValueFromFile(fileName);

            try {
                String indexName = "result-predict-trend-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                openSearchService.indexGeneric(indexName, prediction);
            } catch (Exception e) {
                System.err.println("⚠️ [OpenSearch] Error indexando predicción: " + e.getMessage());
            }

            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Error al procesar el archivo: " + e.getMessage()
            ));
        }
    }


    @Tag(name = "Data Processing")
    @PostMapping("/keyword-extract")
    @Operation(summary = "Keyword extraction service from text", description = "Extracts the most relevant keywords from input text based on term frequency filtering.")
    public ResponseEntity<Object> extractKeywords(@RequestParam String fileName) {
        try {
            File file = new File(UPLOAD_DIR + fileName);
            if (!file.exists()) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado"));
            }

            Gson gson = new Gson();
            List<Map<String, Object>> inputDocs;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                inputDocs = gson.fromJson(reader, List.class);
            }

            if (inputDocs == null || inputDocs.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío o malformado"));
            }

            List<Map<String, Object>> indexedDocs = new ArrayList<>();
            String indexName = "result-keyword-extract-" + fileName.replaceAll("\\W+", "-").toLowerCase();

            for (Map<String, Object> doc : inputDocs) {
                String text = (String) doc.getOrDefault("description", doc.get("text"));
                if (text == null || text.isBlank()) continue;

                List<String> keywords = openSearchService.extractKeywords(text);

                Map<String, Object> resultDoc = new LinkedHashMap<>();
                resultDoc.put("id", doc.get("id"));
                resultDoc.put("timestamp", doc.get("timestamp"));
                resultDoc.put("source_endpoint", "keyword-extract");
                resultDoc.put("keywords", keywords);

                // ✅ Resiliencia al fallo de OpenSearch
                try {
                    openSearchService.indexGeneric(indexName, resultDoc);
                } catch (Exception ex) {
                    System.err.println("⚠️ [OpenSearch] Error indexando doc '" + doc.get("id") + "': " + ex.getMessage());
                }

                indexedDocs.add(resultDoc);
            }

            return ResponseEntity.ok(indexedDocs);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error procesando keywords: " + e.getMessage()));
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Text anonymization service", description = "Automatically removes or masks personal, clinical, or institutional identifiers from free-text documents.")
    @PostMapping("/anonymize-text")
    public ResponseEntity<Map<String, Object>> anonymizeText(@RequestParam String fileName) {
        try {
            File file = new File(UPLOAD_DIR + fileName);
            if (!file.exists()) {
                return ResponseEntity.status(404).body(Map.of("error", "El archivo no fue encontrado"));
            }

            Gson gson = new Gson();
            List<Map<String, Object>> documents;
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                documents = gson.fromJson(reader, List.class);
            }

            if (documents == null || documents.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("error", "El archivo está vacío o malformado"));
            }

            List<Map<String, Object>> results = new ArrayList<>();
            OpenSearchService.TextAnonymizerService anonymizer = new OpenSearchService.TextAnonymizerService();
            String indexName = "result-anonymize-text-" + fileName.replaceAll("\\W+", "-").toLowerCase();

            for (Map<String, Object> doc : documents) {
                String original = (String) doc.get("text");
                if (original == null) continue;

                String anonymized = anonymizer.anonymizeTextFromFileContent(original);

                Map<String, Object> resultDoc = new HashMap<>();
                resultDoc.put("id", doc.get("id"));
                resultDoc.put("timestamp", doc.get("timestamp"));
                resultDoc.put("source_endpoint", "anonymize-text");
                resultDoc.put("anonymized_text", anonymized);

                try {
                    openSearchService.indexGeneric(indexName, resultDoc);
                } catch (Exception ex) {
                    System.err.println("⚠️ [OpenSearch] Fallo al indexar doc: " + doc.get("id") + " - " + ex.getMessage());
                }

                results.add(resultDoc);
            }

            return ResponseEntity.ok(Map.of(
                    "file", fileName,
                    "documents_indexed", results.size(),
                    "anonymized_documents", results
            ));

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo leer o procesar el archivo: " + e.getMessage()));
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Thematic text clustering service", description = "Groups similar text entries into clusters based on shared vocabulary and term frequency (TF).")
    @PostMapping("/clustering")
    public ResponseEntity<Object> clusterData(@RequestParam String fileName) {
        try {
            Map<Integer, List<Map<String, Object>>> clusters = openSearchService.clusterDocumentsFromFile(fileName);

            List<Map<String, Object>> indexedDocs = new ArrayList<>();
            String indexName = "result-clustering-" + fileName.replaceAll("\\W+", "-").toLowerCase();

            for (Map.Entry<Integer, List<Map<String, Object>>> entry : clusters.entrySet()) {
                int clusterId = entry.getKey();
                for (Map<String, Object> doc : entry.getValue()) {
                    doc.put("cluster_id", clusterId);
                    doc.put("source_endpoint", "clustering");
                    doc.putIfAbsent("timestamp", Instant.now().toString());

                    try {
                        openSearchService.indexGeneric(indexName, doc);
                    } catch (Exception ex) {
                        System.err.println("⚠️ [OpenSearch] Fallo indexando documento cluster=" + clusterId + ": " + ex.getMessage());
                    }

                    indexedDocs.add(doc);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("file", fileName);
            response.put("documents_indexed", indexedDocs.size());
            response.put("clusters", clusters);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }




    @Tag(name = "Data Processing")
    @Operation(summary = "Sentiment analysis service for clinical text",description = "Evaluates the sentiment of each sentence in a clinical report and computes an overall emotional tone.")
    @PostMapping("/sentiment-analysis")
    public ResponseEntity<Object> sentimentAnalysis(@RequestParam String fileName) {
        try {
            List<Map<String, Object>> results = openSearchService.analyzeSentimentFromFile(fileName);

            String indexName = "result-sentiment-" + fileName.replaceAll("\\W+", "-").toLowerCase();
            int count = 0;

            for (Map<String, Object> doc : results) {
                try {
                    openSearchService.indexGeneric(indexName, doc);
                    count++;
                } catch (Exception ex) {
                    System.err.println("⚠️ [OpenSearch] No se pudo indexar doc: " + doc.get("id") + " → " + ex.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "file", fileName,
                    "documents_indexed", count,
                    "sample", results.subList(0, Math.min(3, results.size()))
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }



    @Tag(name = "Data Processing")
    @Operation(summary = "Named entity recognition (NER) service",description = "Identifies and classifies named entities such as people, organizations, dates, or places in clinical text.")
    @PostMapping("/entity-recognition")
    public ResponseEntity<Map<String, Object>> recognizeEntities(@RequestParam String fileName) {
        try {
            List<Map<String, Object>> results = openSearchService.recognizeEntitiesFromJsonFile(fileName);
            String indexName = "result-entities-" + fileName.replaceAll("\\W+", "-").toLowerCase();

            int count = 0;
            for (Map<String, Object> doc : results) {
                try {
                    openSearchService.indexGeneric(indexName, doc);
                    count++;
                } catch (Exception e) {
                    System.err.println("⚠️ [OpenSearch] Error indexando doc ID " + doc.get("id") + ": " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "file", fileName,
                    "documents_indexed", count,
                    "sample", results.subList(0, Math.min(3, results.size()))
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error procesando archivo: " + e.getMessage()));
        }
    }



    @Tag(name = "Data Processing")
    @RestController
    @RequestMapping("/api/v1/opensearch")
    public class TextSegmentationController {

        @Autowired
        private OpenSearchService openSearchService;

        @PostMapping("/text-segmentation")
        @Operation(
                summary = "Segmentación semántica de texto clínico",
                description = "Segmenta el texto en bloques como síntomas, antecedentes, recomendaciones y tratamiento."
        )
        public ResponseEntity<Object> segmentText(@RequestParam String fileName) {
            try {
                List<Map<String, Object>> results = openSearchService.segmentTextFromFile(fileName);

                String indexName = "result-text-segmentation-" + fileName.replaceAll("\\W+", "-").toLowerCase();
                int indexedCount = 0;

                // Intentamos indexar sin que Swagger falle si hay error
                try {
                    openSearchService.ensureIndexWithDateMapping(indexName);
                    for (Map<String, Object> doc : results) {
                        openSearchService.indexGeneric(indexName, doc);
                        indexedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ [OpenSearch] Indexación omitida: " + e.getMessage());
                }

                return ResponseEntity.ok(Map.of(
                        "file", fileName,
                        "segments_indexed", indexedCount,
                        "segments", results
                ));

            } catch (FileNotFoundException e) {
                return ResponseEntity.status(404).body(Map.of("error", "Archivo no encontrado: " + fileName));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("error", "Error al segmentar texto: " + e.getMessage()));
            }
        }


    }



}
