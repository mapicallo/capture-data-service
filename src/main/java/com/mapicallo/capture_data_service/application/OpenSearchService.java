package com.mapicallo.capture_data_service.application;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
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
     * Analiza el sentimiento de cada entrada textual contenida en un archivo JSON.
     *
     * <p>Lee el archivo especificado, interpreta su contenido como una lista de documentos
     * con campo "text", y aplica el modelo de análisis de sentimientos de Stanford CoreNLP.
     * Para cada texto, clasifica el sentimiento como Very Negative, Negative, Neutral, Positive o Very Positive,
     * y asigna una puntuación cuantitativa. El resultado incluye además los metadatos originales del documento.
     *
     * <p>Es útil para detectar la orientación emocional general de textos clínicos, comentarios de pacientes
     * o informes médicos en español.
     *
     * @param fileName nombre del archivo JSON previamente cargado, con una lista de documentos con campo "text".
     * @return una lista de mapas, uno por documento, que contienen el sentimiento, puntuación, texto original y metadatos.
     * @throws IOException si el archivo no se encuentra o no puede leerse correctamente.
     */
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


    /**
     * Extrae tripletas semánticas del tipo sujeto–relación–objeto a partir de textos en un archivo JSON.
     * <p>
     * Utiliza el componente KBP (Knowledge Base Population) de Stanford CoreNLP para identificar relaciones explícitas
     * en el texto (por ejemplo, "el paciente toma ibuprofeno").
     * <p>
     * El archivo debe contener una lista de objetos JSON con al menos el campo "text". Si están presentes,
     * también se añaden los campos "id", "timestamp" y "source_endpoint" como metadatos.
     *
     * @param fileName nombre del archivo JSON con los documentos a procesar.
     * @return una cadena JSON con la lista de tripletas extraídas, ordenadas por confianza.
     */
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
     * Aplica reconocimiento de entidades nombradas (NER) a los textos de un archivo JSON.
     * <p>
     * Procesa cada documento del archivo usando Stanford CoreNLP para detectar entidades
     * como PERSON, ORGANIZATION, LOCATION, DATE, etc., y devuelve una lista de objetos
     * enriquecidos con las entidades encontradas.
     *
     * @param fileName Nombre del archivo JSON ubicado en el directorio de subida.
     *                 El archivo debe contener una lista de documentos con al menos un campo "text".
     * @return Lista de mapas con la información original y las entidades reconocidas por documento.
     * @throws IOException Si el archivo no existe o no puede leerse.
     */
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

    /**
     * Analiza un archivo CSV local y calcula estadísticas descriptivas
     * (media, desviación estándar, mínimo, máximo, cantidad de elementos)
     * sobre los campos numéricos detectados.
     *
     * @param fileName Nombre del archivo CSV previamente cargado (ubicado en UPLOAD_DIR)
     * @return JSON con estadísticas por campo numérico o mensaje de error si el archivo no existe o está vacío.
     * @throws IOException Si ocurre un error al leer el archivo.
     *
     * Ejemplo de salida:
     * {
     *   "edad": {
     *     "count": 100,
     *     "mean": 73.2,
     *     "std_dev": 4.5,
     *     "min": 65,
     *     "max": 81
     *   }
     * }
     */
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


    /**
     * Resume un texto clínico seleccionando las frases más representativas
     * según una heurística basada en la frecuencia de palabras (TF).
     *
     * El método realiza los siguientes pasos:
     * 1. Divide el texto en frases.
     * 2. Calcula la frecuencia (TF) de cada palabra ignorando palabras muy cortas.
     * 3. Asigna una puntuación a cada frase según la suma de las frecuencias de sus palabras.
     * 4. Devuelve las 3 frases con mayor puntuación como resumen.
     *
     * @param description Texto de entrada (por ejemplo, informe clínico).
     * @return Mapa con la longitud original (en frases) y la lista de frases resumen.
     */
    public Map<String, Object> summarizeText(String description) {
        if (description == null || description.isEmpty()) {
            return Map.of(
                    "original_length", 0,
                    "summary", List.of()
            );
        }

        // 1. Dividir en frases
        String[] sentences = description.split("(?<=[.!?])\\s+");
        List<String> trimmedSentences = Arrays.stream(sentences)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // 2. Tokenizar palabras y calcular frecuencia (TF)
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String sentence : trimmedSentences) {
            String[] words = sentence.toLowerCase().split("\\W+");
            for (String word : words) {
                if (word.length() > 2) { // omitir palabras muy cortas o stopwords simples
                    wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
                }
            }
        }

        // 3. Asignar puntuación a cada frase
        Map<String, Integer> sentenceScores = new HashMap<>();
        for (String sentence : trimmedSentences) {
            int score = 0;
            String[] words = sentence.toLowerCase().split("\\W+");
            for (String word : words) {
                score += wordFreq.getOrDefault(word, 0);
            }
            sentenceScores.put(sentence, score);
        }

        // 4. Seleccionar las 3 frases con mayor puntuación
        List<String> summary = sentenceScores.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        return Map.of(
                "original_length", trimmedSentences.size(),
                "summary", summary
        );
    }



    /**
     * Predice el siguiente valor de una serie temporal numérica contenida en un archivo CSV.
     *
     * <p>Lee el archivo especificado y busca la primera columna con datos numéricos válidos.
     * Aplica regresión lineal simple (usando Apache Commons Math) sobre los valores encontrados
     * y estima el siguiente valor en la secuencia. Devuelve información como el valor predicho,
     * el último valor real observado, el nombre de la serie y metadatos adicionales.
     *
     * <p>Este método es útil para analizar tendencias simples en datos cuantitativos extraídos
     * de documentos (como frecuencias de términos clínicos o métricas temporales).
     *
     * @param fileName nombre del archivo CSV previamente subido (con encabezado y datos numéricos).
     * @return mapa con la predicción, último valor, nombre de serie, timestamp y origen.
     * @throws IOException si el archivo no se encuentra o no puede leerse correctamente.
     */
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
     * Extrae las 10 palabras clave más representativas de un texto utilizando Stanford CoreNLP.
     *
     * Este método tokeniza, lematiza y analiza morfosintácticamente el texto de entrada.
     * Se filtran stopwords comunes en español y se consideran únicamente sustantivos, verbos
     * y adjetivos con longitud superior a 3 caracteres.
     *
     * Es una versión mejorada del enfoque de frecuencia simple, que mejora la calidad semántica
     * de las palabras clave extraídas.
     *
     * @param text Texto libre en español del cual se extraerán las palabras clave.
     * @return Lista de hasta 10 lemas más frecuentes, ordenados por frecuencia descendente.
     */
    public List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Inicializar pipeline si no existe (puede extraerse como singleton si se reutiliza mucho)
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
        props.setProperty("tokenize.language", "es");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // Lista de stopwords en español (puedes ampliar o externalizar)
        Set<String> stopwords = Set.of(
                "para", "como", "este", "esta", "con", "los", "las", "del", "que", "una", "por",
                "entre", "sobre", "pero", "tiene", "han", "ser", "más", "menos", "muy", "sin", "a",
                "en", "de", "y", "o", "al", "es", "se", "el", "la", "un", "lo", "su"
        );

        // Procesar texto con CoreNLP
        Annotation document = new Annotation(text.toLowerCase());
        pipeline.annotate(document);

        Map<String, Integer> lemmaFrequency = new HashMap<>();

        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);

                // Filtrar por stopwords, longitud mínima y tipo POS (sustantivos, verbos, adjetivos, etc.)
                if (lemma.length() > 3 && !stopwords.contains(lemma) &&
                        (pos.startsWith("N") || pos.startsWith("V") || pos.startsWith("J"))) {
                    lemmaFrequency.put(lemma, lemmaFrequency.getOrDefault(lemma, 0) + 1);
                }
            }
        }

        // Devolver top 10 términos más frecuentes
        return lemmaFrequency.entrySet().stream()
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
     * Agrupa documentos textuales en dos clústeres temáticos usando K-Means.
     * <p>
     * Este método lee un archivo JSON con una lista de documentos (cada uno con un campo "text"),
     * tokeniza el contenido, construye vectores de frecuencia de términos (TF) y aplica el algoritmo
     * K-Means (K=2) para agrupar los documentos según su similitud textual.
     * <p>
     * El resultado es un mapa donde cada clave es un ID de clúster (0 o 1) y su valor asociado es
     * la lista de documentos asignados a ese clúster, incluyendo el campo adicional "cluster_id".
     *
     * @param fileName nombre del archivo JSON previamente subido (ruta fija en el servidor).
     * @return un mapa con dos claves (0 y 1) representando los clústeres y los documentos agrupados.
     * @throws IOException si el archivo no existe o hay un error de lectura.
     * @throws IllegalArgumentException si el archivo contiene menos de dos documentos.
     */
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
