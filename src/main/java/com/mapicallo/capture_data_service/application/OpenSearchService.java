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
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;


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






}
