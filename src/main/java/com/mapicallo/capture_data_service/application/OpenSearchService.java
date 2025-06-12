package com.mapicallo.capture_data_service.application;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import jakarta.annotation.PostConstruct;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class OpenSearchService {

    // ================================
    // Variables Globales y Dependencias
    // ================================

    //carpeta donde se alojan los ficheros subidos por el usuario.
    private static final String UPLOAD_DIR = "C:/uploaded_files/";

    // pipeline para análisis de sentimiento con CoreNLP.
    private StanfordCoreNLP sentimentPipeline;

    //cliente oficial de OpenSearch para operaciones CRUD.
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private TextAnonymizerService textAnonymizerService;



    // ================================
    // Indexación de Documentos
    // ================================

    //Permite indexar un documento individual de forma controlada (nombre, descripción, timestamp).
    //Usa XContentFactory para generar el JSON de forma programática.
    public String indexDocument(String indexName, String documentId, Map<String, Object> document) throws IOException {
        // Construir el documento usando XContentFactory.jsonBuilder()
        IndexRequest request = new IndexRequest(indexName)
                .id(documentId)
                .source(
                        XContentFactory.jsonBuilder()
                                .startObject()
                                .field("name", document.get("name"))
                                .field("description", document.get("description"))
                                .field("timestamp", document.get("timestamp"))
                                .endObject()
                );

        // Indexar el documento
        IndexResponse response = restHighLevelClient.index(request, RequestOptions.DEFAULT);
        return response.getResult().name(); // Resultado de la operación: CREATED, UPDATED, etc.
    }


    /**
     * Leer archivo JSON.
     */
    //Soporte para leer ficheros .json (simulado) y .csv.
    //En el CSV se asume la primera fila como cabecera y se mapea cada fila a un documento.
    public Map<String, Object> readJsonFile(File file) throws IOException {
        // Simulación: En un caso real, deberías usar una biblioteca como Jackson para leer el archivo JSON
        Map<String, Object> document = new HashMap<>();
        document.put("name", "Sample Name");
        document.put("description", "Sample Description");
        document.put("timestamp", "2024-12-25T20:00:00Z");
        return document;
    }


    /**
     * Procesar archivo CSV.
     */
    public void processCsvFile(File file, String indexName) throws IOException {
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            String[] headers = reader.readNext();
            String[] line;
            int count = 0;

            while ((line = reader.readNext()) != null) {
                Map<String, Object> document = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    document.put(headers[i], line[i]);
                }
                indexDocument(indexName, String.valueOf(count++), document);
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Realiza análisis de sentimiento sobre el contenido del archivo.
     */
    //Inicializa el pipeline de Stanford NLP al arrancar el servicio.
    //Lee los textos y devuelve un análisis con:
    //distribución de sentimientos, puntuación promedio, sentimiento general (summary_sentiment)
    @PostConstruct
    public void initSentimentPipeline() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,parse,sentiment");
        this.sentimentPipeline = new StanfordCoreNLP(props);
    }

    public List<Map<String, Object>> analyzeSentimentFromFile(String fileName) throws IOException {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) throw new FileNotFoundException("Archivo no encontrado: " + fileName);

        Gson gson = new Gson();
        List<Map<String, Object>> documents;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            documents = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>() {}.getType());
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> doc : documents) {
            String text = (String) doc.get("text");
            if (text == null || text.isBlank()) continue;

            Map<String, Object> sentiment = analyzeTextSentiment(text);
            sentiment.put("id", doc.get("id"));
            sentiment.put("timestamp", doc.get("timestamp"));
            sentiment.put("source_endpoint", doc.get("source_endpoint"));
            sentiment.put("original_text", text);
            results.add(sentiment);
        }

        return results;
    }

    private Map<String, Object> analyzeTextSentiment(String text) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> sentimentCount = new HashMap<>(Map.of(
                "Very Negative", 0,
                "Negative", 0,
                "Neutral", 0,
                "Positive", 0,
                "Very Positive", 0
        ));

        List<Integer> scores = new ArrayList<>();
        CoreDocument document = new CoreDocument(text);
        sentimentPipeline.annotate(document);

        for (CoreSentence sentence : document.sentences()) {
            String sentiment = sentence.sentiment();
            int score = sentimentToScore(sentiment);
            sentimentCount.put(sentiment, sentimentCount.getOrDefault(sentiment, 0) + 1);
            scores.add(score);
        }

        double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(2.0);
        String label = scoreToLabel((int) Math.round(avg));

        result.put("summary_sentiment", label);
        result.put("sentences_analyzed", scores.size());
        result.put("distribution", sentimentCount);
        result.put("average_score", avg);

        return result;
    }

    private int sentimentToScore(String sentiment) {
        return switch (sentiment) {
            case "Very Negative" -> 0;
            case "Negative"      -> 1;
            case "Neutral"       -> 2;
            case "Positive"      -> 3;
            case "Very Positive" -> 4;
            default              -> 2;
        };
    }

    private String scoreToLabel(int score) {
        return switch (score) {
            case 0 -> "Very Negative";
            case 1 -> "Negative";
            case 2 -> "Neutral";
            case 3 -> "Positive";
            case 4 -> "Very Positive";
            default -> "Neutral";
        };
    }

    //Extracción de Tripletas Semánticas
    //Usa KBP de Stanford NLP para extraer tripletas sujeto–relación–objeto.
    //Devuelve una lista ordenada por confianza, útil para crear grafos semánticos.
    public String extractTriplesFromFile(String fileName) {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) {
            return "{\"error\": \"Archivo no encontrado: " + fileName + "\"}";
        }

        Gson gson = new Gson();
        List<Map<String, Object>> entries;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            entries = gson.fromJson(reader, List.class);
        } catch (Exception e) {
            return "{\"error\": \"No se pudo leer el archivo como lista JSON: " + e.getMessage() + "\"}";
        }

        if (entries == null || entries.isEmpty()) {
            return "{\"error\": \"Archivo JSON vacío o malformado\"}";
        }

        // Configurar pipeline CoreNLP una vez
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref,kbp");
        props.setProperty("kbp.language", "es");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        List<Map<String, Object>> allTriples = new ArrayList<>();

        for (Map<String, Object> entry : entries) {
            String text = (String) entry.get("text");
            if (text == null || text.isBlank()) continue;

            Annotation doc = new Annotation(text);
            pipeline.annotate(doc);

            List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
            if (sentences == null) continue;

            for (CoreMap sentence : sentences) {
                Collection<RelationTriple> relations = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
                if (relations != null) {
                    for (RelationTriple triple : relations) {
                        Map<String, Object> tripleMap = new LinkedHashMap<>();
                        tripleMap.put("subject", triple.subjectGloss());
                        tripleMap.put("relation", triple.relationGloss());
                        tripleMap.put("object", triple.objectGloss());
                        tripleMap.put("confidence", triple.confidence);

                        // ➕ Adjuntar metadatos del registro original
                        tripleMap.put("id", entry.get("id"));
                        tripleMap.put("timestamp", entry.get("timestamp"));
                        tripleMap.put("source_endpoint", entry.get("source_endpoint"));

                        allTriples.add(tripleMap);
                    }
                }
            }
        }

        allTriples.sort((a, b) -> Double.compare((Double) b.get("confidence"), (Double) a.get("confidence")));
        Gson pretty = new GsonBuilder().setPrettyPrinting().create();
        return pretty.toJson(allTriples);
    }


    /**
     * Reconoce entidades nombradas como personas, lugares, instituciones, etc.
     */
    //Reconoce entidades clínicas (personas, fechas, hospitales, etc.)
    //Usa CoreEntityMention para detectar y clasificar entidades.
    public List<Map<String, Object>> recognizeEntitiesFromJsonFile(String fileName) throws IOException {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) throw new FileNotFoundException("Archivo no encontrado: " + fileName);

        Gson gson = new Gson();
        List<Map<String, Object>> documents;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            documents = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>() {}.getType());
        }

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> doc : documents) {
            String text = (String) doc.get("text");
            if (text == null || text.isBlank()) continue;

            CoreDocument document = new CoreDocument(text);
            pipeline.annotate(document);

            Map<String, List<String>> entityMap = new HashMap<>();
            for (CoreEntityMention em : document.entityMentions()) {
                entityMap.computeIfAbsent(em.entityType(), k -> new ArrayList<>()).add(em.text());
            }
            entityMap.replaceAll((k, v) -> v.stream().distinct().toList());

            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("id", doc.get("id"));
            enriched.put("timestamp", doc.get("timestamp"));
            enriched.put("source_endpoint", doc.get("source_endpoint"));
            enriched.put("entities", entityMap);
            enriched.put("original_text", text);

            results.add(enriched);
        }

        return results;
    }


    //Segmentación de Texto Clínico
    //Divide el texto en bloques lógicos:
    //síntomas, antecedentes, tratamiento, recomendaciones
    //Basado en reglas heurísticas y presencia de palabras clave.
    public List<Map<String, Object>> segmentTextFromFile(String fileName) throws IOException {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) {
            throw new FileNotFoundException("Archivo no encontrado: " + fileName);
        }

        Gson gson = new Gson();
        List<Map<String, Object>> documents;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            documents = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>() {}.getType());
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> doc : documents) {
            String text = (String) doc.get("text");
            String timestamp = (String) doc.get("timestamp");
            String id = (String) doc.get("id");
            String source = (String) doc.get("source_endpoint");

            if (text == null || text.isBlank()) continue;

            // Heurística para segmentar
            String[] sentences = text.split("(?<=[.!?])\\s+");
            Map<String, String> segments = new LinkedHashMap<>();

            for (String sentence : sentences) {
                String s = sentence.trim().toLowerCase();

                if (s.contains("síntoma") || s.contains("refiere") || s.contains("dolor") || s.contains("fiebre")) {
                    segments.put("síntomas", sentence.trim());
                } else if (s.contains("antecedente") || s.contains("historia clínica")) {
                    segments.put("antecedentes", sentence.trim());
                } else if (s.contains("recomienda") || s.contains("sugiere") || s.contains("aconseja")) {
                    segments.put("recomendaciones", sentence.trim());
                } else if (s.contains("prescribe") || s.contains("administra") || s.contains("tratamiento")) {
                    segments.put("tratamiento", sentence.trim());
                }
            }

            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("id", id);
            enriched.put("timestamp", timestamp);
            enriched.put("source_endpoint", source);
            enriched.put("original_text", text);
            enriched.put("segments", segments);

            results.add(enriched);
        }

        return results;
    }

    //Creación de Índice con Mapeo
    //Crea un índice con mapeo explícito de campos (timestamp, id, etc.).
    //Evita que OpenSearch infiera automáticamente los tipos.
    public void ensureIndexWithDateMapping(String indexName) {
        try {
            boolean exists = restHighLevelClient.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
            if (!exists) {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
                createIndexRequest.mapping(
                        Map.of(
                                "properties", Map.of(
                                        "timestamp", Map.of("type", "date"),
                                        "event", Map.of("type", "text"),
                                        "id", Map.of("type", "keyword"),
                                        "source_endpoint", Map.of("type", "keyword")
                                )
                        )
                );
                System.out.println("Mapping to create index: " + createIndexRequest.mappings());
                restHighLevelClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creando el índice '" + indexName + "': " + e.getMessage(), e);
        }
    }

    // Estadísticas de Dataset CSV
    //Calcula métricas estadísticas (media, desviación, min/max).
    //Aplica únicamente sobre campos numéricos detectados.
    public String summarizeBigDataFromFile(String fileName) throws IOException {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) {
            return "Archivo no encontrado: " + fileName;
        }

        Map<String, List<Double>> numericFields = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine == null) return "Archivo vacío";
            String[] headers = headerLine.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                for (int i = 0; i < values.length; i++) {
                    try {
                        double val = Double.parseDouble(values[i]);
                        numericFields.computeIfAbsent(headers[i], k -> new ArrayList<>()).add(val);
                    } catch (NumberFormatException ignored) {
                        // Ignorar campos no numéricos
                    }
                }
            }
        }

        Map<String, Map<String, Double>> stats = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : numericFields.entrySet()) {
            List<Double> vals = entry.getValue();
            double sum = vals.stream().mapToDouble(Double::doubleValue).sum();
            double mean = sum / vals.size();
            double variance = vals.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / vals.size();
            double stdDev = Math.sqrt(variance);

            stats.put(entry.getKey(), Map.of(
                    "count", (double) vals.size(),
                    "mean", mean,
                    "std_dev", stdDev,
                    "min", vals.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                    "max", vals.stream().mapToDouble(Double::doubleValue).max().orElse(0)
            ));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(stats);
    }


    //Resumen de Texto
    //Extrae las primeras 3 frases de un texto.
    //Método simplificado de resumen heurístico para casos clínicos.
    public Map<String, Object> summarizeText(String description) {
        if (description == null || description.isEmpty()) {
            return Map.of(
                    "original_length", 0,
                    "summary", List.of()
            );
        }


        String[] sentences = description.split("\\.\\s*");
        List<String> trimmed = Arrays.stream(sentences)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        int maxSentences = Math.min(3, trimmed.size());
        List<String> summary = trimmed.subList(0, maxSentences);

        return Map.of(
                "original_length", trimmed.size(),
                "summary", summary
        );
    }

    //Predicción de Tendencias
    //Usa SimpleRegression para prever el siguiente valor en una serie temporal.
    //Se apoya en Apache Commons Math.
    public Map<String, Object> predictNextValueFromFile(String fileName) throws IOException {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) throw new FileNotFoundException("Archivo no encontrado: " + fileName);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("El archivo está vacío.");
            String[] headers = headerLine.split(",");

            // Identificar campos numéricos válidos
            Map<String, List<Double>> numericColumns = new HashMap<>();
            for (String header : headers) numericColumns.put(header, new ArrayList<>());

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                for (int i = 0; i < values.length; i++) {
                    try {
                        double val = Double.parseDouble(values[i]);
                        numericColumns.get(headers[i]).add(val);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            for (Map.Entry<String, List<Double>> entry : numericColumns.entrySet()) {
                String columnName = entry.getKey();
                List<Double> values = entry.getValue();
                if (values.size() < 2) continue;

                SimpleRegression regression = new SimpleRegression();
                for (int i = 0; i < values.size(); i++) {
                    regression.addData(i + 1, values.get(i));
                }

                double nextX = values.size() + 1;
                double prediction = regression.predict(nextX);

                Map<String, Object> result = new HashMap<>();
                result.put("series", columnName);
                result.put("predicted_value", prediction);
                result.put("last_value", values.get(values.size() - 1));
                result.put("timestamp", Instant.now().toString());
                result.put("fileName", fileName);
                result.put("source_endpoint", "predict-trend");
                return result;
            }

            throw new IllegalArgumentException("No se encontraron columnas numéricas válidas.");
        }
    }

    //Operaciones sobre Índices
    //Permiten listar índices y eliminar de forma segura.
    //Operaciones comunes para administrar el backend de OpenSearch.
    public Map<String, Long> listIndicesWithDocumentCount() throws IOException {
        // Obtener el listado de índices
        String[] indices = restHighLevelClient.indices()
                .get(new GetIndexRequest("*"), RequestOptions.DEFAULT)
                .getIndices();

        Map<String, Long> indexDocumentCount = new HashMap<>();

        // Para cada índice, obtener la cantidad de documentos
        for (String index : indices) {
            CountRequest countRequest = new CountRequest(index);
            long documentCount = restHighLevelClient.count(countRequest, RequestOptions.DEFAULT).getCount();
            indexDocumentCount.put(index, documentCount);
        }

        return indexDocumentCount;
    }


    public boolean deleteIndex(String indexName) throws IOException {
        // Verificar si el índice existe
        boolean exists = restHighLevelClient.indices()
                .exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);

        if (!exists) {
            return false; // Índice no existe
        }

        // Intentar eliminar el índice
        restHighLevelClient.indices().delete(new DeleteIndexRequest(indexName), RequestOptions.DEFAULT);

        // Verificar nuevamente para confirmar que fue eliminado
        return !restHighLevelClient.indices()
                .exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
    }



    /**
     * Extrae palabras clave de un archivo de texto o JSON.
     */
    //TF simple: frecuencia de palabras de longitud > 3.
    //Filtrado básico de stopwords comunes en español.
    public List<String> extractKeywords(String text) {
        Map<String, Integer> tf = new HashMap<>();
        String[] tokens = text.toLowerCase().split("\\s+");

        for (String token : tokens) {
            if (token.length() > 3 && !List.of("para", "como", "este", "esta", "con", "los", "las").contains(token)) {
                tf.put(token, tf.getOrDefault(token, 0) + 1);
            }
        }

        return tf.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }


    /**
     * Anonimiza texto médico o sensible en un archivo.
     */
    //Remueve información sensible:
        //nombres, doctores, fechas, hospitales
    // Usa expresiones regulares específicas.
    /*public static class TextAnonymizerService {

        public String anonymizeTextFromFileContent(String input) {
            String anonymized = input;

            // Reemplazar profesionales médicos
            anonymized = anonymized.replaceAll("\\b(Dra?\\.?\\s+\\p{Lu}\\p{L}+)", "[PROFESIONAL]");

            // Reemplazar nombres tipo "Juan Pérez"
            anonymized = anonymized.replaceAll("\\b(\\p{Lu}\\p{L}+\\s+\\p{Lu}\\p{L}+)\\b", "[NOMBRE]");

            // Reemplazar nombres de hospitales
            anonymized = anonymized.replaceAll("\\b(Hospital|Clínica)\\s+[\\p{L}\\s]+", "[CENTRO_MEDICO]");

            // Reemplazar fechas comunes
            anonymized = anonymized.replaceAll("\\b\\d{2}/\\d{2}/\\d{4}\\b", "[FECHA]");
            anonymized = anonymized.replaceAll("\\b\\d{4}-\\d{2}-\\d{2}\\b", "[FECHA]");

            return anonymized;
        }
    }*/


    /**
     * Agrupa entradas de texto en clústeres temáticos.
     */
    //Tokeniza documentos, genera vectores TF y aplica K-means con K=2.
    //Devuelve estructura {cluster_id → lista de documentos}.
    public Map<Integer, List<Map<String, Object>>> clusterDocumentsFromFile(String fileName) throws IOException {
        String path = "C:/uploaded_files/" + fileName;

        // Leer JSON con JsonReader en modo lenient
        Gson gson = new Gson();
        JsonReader jsonReader = new JsonReader(new FileReader(path));
        jsonReader.setLenient(true);

        List<Map<String, Object>> documents = gson.fromJson(
                jsonReader,
                new TypeToken<List<Map<String, Object>>>() {}.getType()
        );

        if (documents == null || documents.size() < 2) {
            throw new IllegalArgumentException("Se requieren al menos 2 documentos para clustering.");
        }

        // Tokenizar y construir vocabulario
        Set<String> vocabulary = new HashSet<>();
        List<List<String>> tokenizedDocs = new ArrayList<>();
        for (Map<String, Object> doc : documents) {
            String text = (String) doc.get("text");
            List<String> tokens = Arrays.stream(text.toLowerCase().split("\\W+"))
                    .filter(t -> t.length() > 2)
                    .collect(Collectors.toList());
            tokenizedDocs.add(tokens);
            vocabulary.addAll(tokens);
        }

        List<String> vocabList = new ArrayList<>(vocabulary);

        // Construir vectores TF
        List<ClusterableDocument> vectorDocs = new ArrayList<>();
        for (List<String> tokens : tokenizedDocs) {
            double[] vector = new double[vocabList.size()];
            for (int j = 0; j < vocabList.size(); j++) {
                vector[j] = Collections.frequency(tokens, vocabList.get(j));
            }
            vectorDocs.add(new ClusterableDocument(vector));
        }

        // Clustering con K=2
        KMeansPlusPlusClusterer<ClusterableDocument> clusterer = new KMeansPlusPlusClusterer<>(2);
        List<CentroidCluster<ClusterableDocument>> result = clusterer.cluster(vectorDocs);

        // Asignar documentos a clusters
        Map<Integer, List<Map<String, Object>>> clusters = new HashMap<>();
        for (int i = 0; i < result.size(); i++) {
            List<Map<String, Object>> clusterGroup = new ArrayList<>();
            for (ClusterableDocument docVec : result.get(i).getPoints()) {
                int originalIdx = vectorDocs.indexOf(docVec);
                if (originalIdx != -1) {
                    Map<String, Object> original = new LinkedHashMap<>(documents.get(originalIdx));
                    original.put("cluster_id", i);
                    clusterGroup.add(original);
                }
            }
            clusters.put(i, clusterGroup);
        }

        return clusters;
    }


    public static class ClusterableDocument implements Clusterable {
        private final double[] point;

        public ClusterableDocument(double[] point) {
            this.point = point;
        }

        @Override
        public double[] getPoint() {
            return point;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ClusterableDocument other)) return false;
            return Arrays.equals(point, other.point);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(point);
        }
    }


    //Indexación Genérica
    //Permite indexar cualquier documento sin estructura rígida.
    //Usado internamente por todos los endpoints que procesan archivos.
    public String indexGeneric(String indexName, Map<String, Object> payload) throws IOException {
        IndexRequest request = new IndexRequest(indexName)
                .id(UUID.randomUUID().toString())
                .source(payload);
        IndexResponse response = restHighLevelClient.index(request, RequestOptions.DEFAULT);
        return response.getResult().name();
    }

}
