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
    private final String testFileName1 = "test_extract_triples.txt";
    private final String testFileName2 = "neumologia_datos_pacientes.csv";

    @BeforeEach
    void setupTestFile() throws Exception {
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        File file1 = new File(uploadDir + testFileName1);
        try (FileWriter writer = new FileWriter(file1)) {
            writer.write("Dr. Smith diagnosed John Doe with bronchial asthma.\n");
            writer.write("Mary Johnson works at General Hospital as a pulmonologist.");
        }

        File file2 = new File(uploadDir + testFileName2);
        try (FileWriter writer = new FileWriter(file2)) {
            writer.write("id_paciente,edad,fev1,fvc,ratio_fev1_fvc,saturacion_oxigeno\n");
            writer.write("1,65,1.8,3.2,0.56,93\n");
            writer.write("2,72,2.1,3.6,0.58,92\n");
            writer.write("3,58,2.5,3.9,0.64,96\n");
            writer.write("4,80,1.6,2.8,0.57,90\n");
            writer.write("5,67,2.2,3.5,0.63,94\n");
            writer.write("6,75,1.9,3.0,0.63,91\n");
            writer.write("7,69,2.0,3.3,0.61,95\n");
        }




    }

    @Test
    void shouldExtractTriplesFromTextFile() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/extract-triples")
                        .param("fileName", testFileName1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("subject")))
                .andExpect(content().string(containsString("relation")))
                .andExpect(content().string(containsString("object")));
    }

    @Test
    void shouldSummarizeBigDataFromCsv() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/bigdata/summarize")
                        .param("fileName", testFileName2))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("edad")))
                .andExpect(content().string(containsString("mean")))
                .andExpect(content().string(containsString("std_dev")))
                .andExpect(content().string(containsString("saturacion_oxigeno")));
    }


    @Test
    void shouldSummarizeAiFromJsonFile() throws Exception {
        String fileName = "casos_clinicos.json";
        String jsonContent = """
        [
          {
            "name": "Caso1",
            "description": "El paciente presenta disnea progresiva desde hace dos semanas. Se ha iniciado tratamiento con broncodilatadores. La evolución ha sido favorable tras la intervención médica.",
            "timestamp": "2025-05-01T10:00:00Z"
          },
          {
            "name": "Caso2",
            "description": "Paciente diagnosticado con EPOC moderada. Se recomienda seguimiento con pruebas espirométricas. No se evidencian complicaciones agudas.",
            "timestamp": "2025-05-02T11:30:00Z"
          },
          {
            "name": "Caso3",
            "description": "El paciente ingresa con tos seca persistente y fiebre. Se descarta neumonía tras análisis radiológicos. Se prescribe antibiótico de amplio espectro como medida preventiva.",
            "timestamp": "2025-05-03T09:45:00Z"
          }
        ]
        """;

        // Crear archivo de prueba en el directorio UPLOAD_DIR
        File dir = new File("C:/uploaded_files/");
        if (!dir.exists()) dir.mkdirs();
        Files.writeString(Path.of("C:/uploaded_files/" + fileName), jsonContent);

        mockMvc.perform(post("/api/v1/opensearch/ai/summarize")
                        .param("fileName", fileName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isArray())
                .andExpect(jsonPath("$.summary[0]").value(org.hamcrest.Matchers.containsString("El paciente presenta disnea")))
                .andExpect(jsonPath("$.original_length").isNumber());
    }


}
