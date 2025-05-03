package com.mapicallo.capture_data_service.application;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


@Service
public class OpenSearchService {

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

    // Leer archivo JSON
    public Map<String, Object> readJsonFile(File file) throws IOException {
        // Simulación: En un caso real, deberías usar una biblioteca como Jackson para leer el archivo JSON
        Map<String, Object> document = new HashMap<>();
        document.put("name", "Sample Name");
        document.put("description", "Sample Description");
        document.put("timestamp", "2024-12-25T20:00:00Z");
        return document;
    }

    // Procesar archivo CSV
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





}
