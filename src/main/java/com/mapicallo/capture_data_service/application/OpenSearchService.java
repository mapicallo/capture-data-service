package com.mapicallo.capture_data_service.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.GetIndexResponse;
import org.opensearch.client.core.CountRequest;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OpenSearchService {

    @Autowired
    private RestHighLevelClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String indexDocument(String index, String documentId, Map<String, Object> documentMap) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(documentMap); // Convert Map to JSON

        IndexRequest request = new IndexRequest(index);

        if (documentId != null && !documentId.isEmpty()) {
            request.id(documentId);
        }

        // Usamos directamente la fuente JSON como bytes
        request.source(json, "application/json");

        IndexResponse response = client.index(request, RequestOptions.DEFAULT);
        return response.getResult().name();
    }



    public Map<String, Long> listIndicesWithDocumentCount() throws IOException {
        GetIndexRequest request = new GetIndexRequest("*");
        GetIndexResponse response = client.indices().get(request, RequestOptions.DEFAULT);

        return Arrays.stream(response.getIndices())
                .collect(Collectors.toMap(
                        index -> index,
                        index -> {
                            try {
                                CountRequest countRequest = new CountRequest(index);
                                return client.count(countRequest, RequestOptions.DEFAULT).getCount();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }
                ));
    }

    public boolean deleteIndex(String indexName) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse response = client.indices().delete(request, RequestOptions.DEFAULT);
        return response.isAcknowledged();
    }

    public Map<String, Object> readJsonFile(File file) throws IOException {
        return objectMapper.readValue(file, Map.class);
    }

    public String readFileContentAsString(File file) throws IOException {
        //return new String(java.nio.file.Files.readAllBytes(file.toPath()));
        // Asegura lectura en UTF-8
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    public void processCsvFile(File file, String indexName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String[] headers = br.readLine().split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                Map<String, Object> doc = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    doc.put(headers[i], values[i]);
                }
                indexDocument(indexName, null, doc);
            }
        }
    }
}
