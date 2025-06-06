package com.mapicallo.capture_data_service.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OpenSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final String uploadDir = "C:/uploaded_files/";
    private final String testFileName = "test_extract_triples.txt";

    @BeforeEach
    void setupTestFile() throws Exception {
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        File file = new File(uploadDir + testFileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Dr. Smith diagnosed John Doe with bronchial asthma.\n");
            writer.write("Mary Johnson works at General Hospital as a pulmonologist.");
        }
    }

    @Test
    void shouldExtractTriplesFromTextFile() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/extract-triples")
                        .param("fileName", testFileName))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("subject")))
                .andExpect(content().string(containsString("relation")))
                .andExpect(content().string(containsString("object")));
    }
}
