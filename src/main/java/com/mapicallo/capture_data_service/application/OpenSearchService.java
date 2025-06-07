package com.mapicallo.capture_data_service.application;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class OpenSearchService {

    private static final String UPLOAD_DIR = "C:/uploaded_files/";

    @Autowired
    private RestHighLevelClient restHighLevelClient;

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


    public double predictNextValue(File file) throws IOException {
        List<Double> values = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // cabecera
            String line;
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                values.add(Double.parseDouble(fields[1])); // columna numérica
            }
        }

        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < values.size(); i++) {
            regression.addData(i + 1, values.get(i));
        }
        double nextX = values.size() + 1;
        return regression.predict(nextX);
    }



    public String extractTriplesFromFile(String fileName) {
        String filePath = UPLOAD_DIR + fileName;
        String text = readFile(filePath);
        if (text == null || text.isBlank()) {
            return "{\"error\": \"El archivo está vacío o no se pudo leer correctamente.\"}";
        }

        // Configurar el pipeline de CoreNLP
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref,kbp");
        props.setProperty("kbp.language", "es"); // Si el texto está en español, cambiar a "es"
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // Crear anotación del texto
        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        // Extraer relaciones semánticas
        List<Map<String, Object>> triples = new ArrayList<>();
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            Collection<RelationTriple> relations = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
            if (relations != null) {
                for (RelationTriple triple : relations) {
                    Map<String, Object> tripleMap = new LinkedHashMap<>();
                    tripleMap.put("subject", triple.subjectGloss());
                    tripleMap.put("relation", triple.relationGloss());
                    tripleMap.put("object", triple.objectGloss());
                    tripleMap.put("confidence", triple.confidence);
                    triples.add(tripleMap);
                }
            }
        }

        // Ordenar por nivel de confianza descendente
        triples.sort((a, b) -> Double.compare((Double) b.get("confidence"), (Double) a.get("confidence")));


        // Convertir a JSON legible
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(triples);
    }


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


    public String summarizeTextFromFile(String fileName) throws IOException {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) {
            return "{\"error\": \"Archivo no encontrado: " + fileName + "\"}";
        }

        // Leer como lista de objetos JSON
        Gson gson = new Gson();
        List<Map<String, Object>> documents;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            documents = gson.fromJson(reader, List.class);
        }

        if (documents == null || documents.isEmpty()) {
            return "{\"error\": \"Archivo JSON vacío o malformado\"}";
        }

        List<String> sentences = new ArrayList<>();
        for (Map<String, Object> doc : documents) {
            Object descObj = doc.get("description");
            if (descObj instanceof String) {
                String[] parts = ((String) descObj).split("\\.\\s*");
                sentences.addAll(Arrays.asList(parts));
            }
        }

        int maxSentences = Math.min(3, sentences.size());
        List<String> summary = sentences.subList(0, maxSentences);

        Map<String, Object> response = Map.of(
                "original_length", sentences.size(),
                "summary", summary
        );
        Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
        return prettyGson.toJson(response);
    }



    public Map<String, Double> predictNextValueFromFile(String fileName) throws IOException {
        File file = new File(UPLOAD_DIR + fileName);
        if (!file.exists()) throw new FileNotFoundException("Archivo no encontrado: " + fileName);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("El archivo está vacío.");
            String[] headers = headerLine.split(",");

            // Mapa para cada columna numérica
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

            // Seleccionamos la primera columna numérica válida
            for (Map.Entry<String, List<Double>> entry : numericColumns.entrySet()) {
                List<Double> values = entry.getValue();
                if (values.size() < 2) continue;

                SimpleRegression regression = new SimpleRegression();
                for (int i = 0; i < values.size(); i++) {
                    regression.addData(i + 1, values.get(i));
                }

                double nextX = values.size() + 1;
                double prediction = regression.predict(nextX);

                return Map.of("predicted_value", prediction, "last_value", values.get(values.size() - 1));
            }

            throw new IllegalArgumentException("No se encontraron columnas numéricas válidas.");
        }
    }




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


    public String readTextFromFile(File file) throws IOException {
        if (file.getName().endsWith(".json")) {
            // Leer campo "description" o concatenar textos
            String content = Files.readString(file.toPath());
            return content.replaceAll("[^\\p{L}\\p{N}\\s]", " "); // limpiar símbolos
        } else if (file.getName().endsWith(".txt")) {
            return Files.readString(file.toPath());
        } else {
            throw new IOException("Unsupported file type for keyword extraction.");
        }
    }

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
     * Extrae palabras clave de un archivo de texto o JSON.
     */
    public String extractKeywordsFromFile(String fileName) {
        return "Keywords extracted (stub)";
    }

    /**
     * Anonimiza texto médico o sensible en un archivo.
     */
    public String anonymizeTextFromFile(String fileName) {
        return "Text anonymized (stub)";
    }

    /**
     * Agrupa entradas de texto en clústeres temáticos.
     */
    public String clusterDataFromFile(String fileName) {
        return "Data clustered (stub)";
    }

    /**
     * Realiza análisis de sentimiento sobre el contenido del archivo.
     */
    public String analyzeSentimentFromFile(String fileName) {
        return "Sentiment analyzed (stub)";
    }

    /**
     * Reconoce entidades nombradas como personas, lugares, instituciones, etc.
     */
    public String recognizeEntitiesFromFile(String fileName) {
        return "Entities recognized (stub)";
    }

    /**
     * Construye una línea de tiempo a partir de eventos encontrados en el texto.
     */
    public String buildTimelineFromFile(String fileName) {
        return "Timeline built (stub)";
    }




    private String readFile(String filePath) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(" ");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return content.toString().trim();
    }

    private List<Map<String, Object>> readJsonArrayFromFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            Gson gson = new Gson();
            return gson.fromJson(reader, List.class);
        }
    }







}
