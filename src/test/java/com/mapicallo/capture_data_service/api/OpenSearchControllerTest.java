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
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
class OpenSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final String uploadDir = "C:/uploaded_files/";
    private final String testFileName1 = "test_extract_triples.txt";
    private final String testFileName2 = "neumologia_datos_pacientes.csv";

    private final String testFileName3 = "tendencia_consultas.csv";

    private final String testFileName4 = "registro_clinico_anon.txt";

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


        File file3 = new File(uploadDir + testFileName3);
        try (FileWriter writer = new FileWriter(file3)) {
            writer.write("mes,consultas_realizadas\n");
            writer.write("1,80\n");
            writer.write("2,100\n");
            writer.write("3,120\n");
            writer.write("4,140\n");
            writer.write("5,160\n");
        }


        File file4 = new File(uploadDir + testFileName4);
        try (FileWriter writer = new FileWriter(file4)) {
            writer.write("El paciente Juan Pérez fue atendido el 12/04/2025 por la Dra. García en el Hospital Central.\n");
            writer.write("Se diagnosticó asma bronquial leve y se recomendó seguimiento en la Clínica San Miguel.");
        }

    }


    @Test
    void shouldAnonymizeSensitiveData() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/anonymize-text")
                        .param("fileName", testFileName4))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Juan Pérez"))))
                .andExpect(content().string(not(containsString("12/04/2025"))))
                .andExpect(content().string(not(containsString("Dra. García"))))
                .andExpect(content().string(not(containsString("Hospital Central"))))
                .andExpect(content().string(containsString("[NOMBRE]")))
                .andExpect(content().string(containsString("[FECHA]")))
                .andExpect(content().string(containsString("[CENTRO_MEDICO]")));
    }



    @Test
    void shouldPredictTrendFromCsvFile() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/predict-trend")
                        .param("fileName", testFileName3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_value").value(160.0))
                .andExpect(jsonPath("$.predicted_value").value(180.0));
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


    @Test
    void shouldExtractKeywordsFromMedicalText() throws Exception {
        String fileName = "informe_respiratorio.txt";
        File testFile = new File(uploadDir + fileName);
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("El paciente presenta síntomas respiratorios como disnea, tos seca y fatiga crónica. ");
            writer.write("Durante el examen clínico, se detectaron ruidos respiratorios anormales. ");
            writer.write("Se recomienda realizar pruebas espirométricas y continuar con tratamiento broncodilatador. ");
            writer.write("No se observaron signos de infección activa.");
        }

        mockMvc.perform(post("/api/v1/opensearch/keyword-extract")
                        .param("fileName", fileName))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("respiratorios")))
                .andExpect(content().string(containsString("espirométricas")))
                .andExpect(content().string(containsString("paciente")));
    }


    @Test
    void shouldClusterTextDataFromFile() throws Exception {
        String testFileName = "clustering_test_input.txt";

        mockMvc.perform(post("/api/v1/opensearch/clustering")
                        .param("fileName", testFileName))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cluster 0")))
                .andExpect(content().string(containsString("Cluster 1")))
                .andExpect(content().string(containsString("bronquitis")))
                .andExpect(content().string(containsString("software hospitalario")));
    }

    @Test
    void shouldAnalyzeSentimentFromFile() throws Exception {
        String fileName = "sentimiento_extremo.txt";
        File dir = new File("C:/uploaded_files/");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Estoy absolutamente feliz con el tratamiento. Me siento mucho mejor.\n");
            writer.write("La atención médica fue excelente, muy profesional y humana.\n");
            writer.write("El paciente está devastado, la evolución ha sido negativa y rápida.\n");
            writer.write("Fue una experiencia horrible, la sala estaba sucia y el trato fue pésimo.\n");
            writer.write("No tengo quejas. Todo ha sido correcto, sin problemas ni complicaciones.\n");
        }

        mockMvc.perform(post("/api/v1/opensearch/sentiment-analysis")
                        .param("fileName", fileName))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("average_score")))
                .andExpect(content().string(containsString("sentences_analyzed")))
                .andExpect(content().string(containsString("summary_sentiment")))
                .andExpect(content().string(containsString("distribution")));
    }






}
