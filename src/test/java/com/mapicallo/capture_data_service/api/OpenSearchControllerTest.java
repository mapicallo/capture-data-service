package com.mapicallo.capture_data_service.api;

import com.mapicallo.capture_data_service.application.OpenSearchService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    private final String testFileName3 = "tendencia_consultas.csv";

    private final String testFileName4 = "registro_clinico_anon.txt";

    private final String testFileName5 = "entidades_clinicas.txt";
    private final String testFileName6 = "historial_clinico.txt";

    private final String testFileName7 = "trend_test.csv";

    @MockBean
    private OpenSearchService openSearchService;



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


        File file5 = new File(uploadDir + testFileName5);
        try (FileWriter writer = new FileWriter(file5)) {
            writer.write("El Dr. Pedro Sánchez trabaja en el Hospital General de Sevilla.\n");
            writer.write("La paciente María González acudió a consulta el 5 de mayo de 2025.\n");
            writer.write("Fue diagnosticada con hipertensión arterial y recomendada a visitar el Centro de Cardiología de Andalucía.\n");
            writer.write("Juan Pérez, otro paciente, fue derivado desde el Hospital Virgen del Rocío a la Clínica Santa Isabel.\n");
            writer.write("La cita de seguimiento fue programada para el 10 de junio de 2025.");
        }

        File file6 = new File(uploadDir + testFileName6);
        try (FileWriter writer = new FileWriter(file6)) {
            writer.write("El 15 de enero de 2023, el paciente fue ingresado en el Hospital Virgen del Rocío por una insuficiencia respiratoria aguda.\n");
            writer.write("Durante la semana del 20 al 27 de febrero de 2023, se le realizaron pruebas espirométricas.\n");
            writer.write("El 10 de marzo de 2023, se inició tratamiento con broncodilatadores.\n");
            writer.write("El 5 de abril de 2023, el paciente mostró mejoría.\n");
            writer.write("El 30 de mayo de 2024, acudió a urgencias con síntomas de fatiga.\n");
        }


    }

   /* @Test
    void shouldBuildTimelineFromTextFile() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/timeline-builder")
                        .param("fileName", testFileName6))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2023")))
                .andExpect(content().string(containsString("2024")))
                .andExpect(content().string(containsString("ingresado")))
                .andExpect(content().string(containsString("urgencias")));
    }*/

    @Test
    public void testTextSegmentationEndpoint() throws Exception {
        // Crear archivo de prueba con contenido mínimo válido
        String content = """
            [
              {
                "id": "seg-001",
                "timestamp": "2025-05-20T10:00:00Z",
                "source_endpoint": "text-segmentation",
                "text": "Paciente con fiebre persistente. Se recomienda reposo absoluto. Se prescribe paracetamol."
              }
            ]
            """;

        Files.writeString(Path.of(uploadDir), content);

        mockMvc.perform(post("/api/v1/opensearch/text-segmentation")
                        .param("fileName", "text-segmentation_test.json")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file").value("text-segmentation_test.json"))
                .andExpect(jsonPath("$.segments_indexed").value(3))
                .andExpect(jsonPath("$.segments").isArray())
                .andExpect(jsonPath("$.segments[0].event").exists());
    }



    @Test
    void shouldRecognizeEntitiesInTextFile() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/entity-recognition")
                        .param("fileName", testFileName5))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PERSON")))
                .andExpect(content().string(containsString("Pedro Sánchez")))
                .andExpect(content().string(containsString("Hospital General")))
                .andExpect(content().string(containsString("2025")));
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
    public void shouldReturnPredictionForValidCsv() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/opensearch/predict-trend")
                        .param("fileName", testFileName7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predicted_value").exists())
                .andExpect(jsonPath("$.last_value").value(85.0))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.source_endpoint").value("predict-trend"))
                .andExpect(jsonPath("$.fileName").value(testFileName7));
    }


    @Test
    public void testKeywordExtractEndpoint() throws Exception {
        String testFileName = "keywords_test.json";

        // Simula que el archivo existe en el directorio de subida
        File file = new File("uploads/" + testFileName);
        if (!file.exists()) {
            Files.write(Paths.get(file.getPath()), """
                [
                  {
                    "id": "kw-001",
                    "timestamp": "2025-05-19T08:00:00Z",
                    "source_endpoint": "keyword-extract",
                    "text": "Paciente con dolor torácico leve. Se recomienda reposo."
                  }
                ]
            """.getBytes(StandardCharsets.UTF_8));
        }

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/opensearch/keyword-extract")
                        .param("fileName", testFileName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords").isArray())
                .andExpect(jsonPath("$.keywords.length()").isNotEmpty());
    }


    @Test
    public void testAnonymizeTextFromFileContent() {
        OpenSearchService.TextAnonymizerService service = new OpenSearchService.TextAnonymizerService();

        String original = "El paciente Juan Pérez fue atendido por la Dra. Gómez en el Hospital Central el 2024-05-01.";
        String anonymized = service.anonymizeTextFromFileContent(original);

        assertNotNull(anonymized);
        assertTrue(anonymized.contains("[NOMBRE]"));
        assertTrue(anonymized.contains("[PROFESIONAL]"));
        assertTrue(anonymized.contains("[CENTRO_MEDICO]"));
        assertTrue(anonymized.contains("[FECHA]"));
        assertFalse(anonymized.contains("Juan Pérez"));
        assertFalse(anonymized.contains("Dra. Gómez"));
        assertFalse(anonymized.contains("Hospital Central"));
    }


    @Test
    void testAnonymizeTextEndpoint_withValidFile() throws Exception {
        String fileName = "anonymize-text-test.json";

        // Suponemos subido este fichero a la carpeta UPLOAD_DIR antes de lanzar el test

        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/anonymize-text")
                        .param("fileName", fileName)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file").value(fileName))
                .andExpect(jsonPath("$.documents_indexed").isNumber())
                .andExpect(jsonPath("$.anonymized_documents").isArray())
                .andExpect(jsonPath("$.anonymized_documents.length()").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.anonymized_documents[0].anonymized_text").exists());
    }


    @Test
    public void testClusteringEndpoint_withValidFile() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/clustering")
                                .param("fileName", "clustering_test.json")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file").value("clustering_test.json"))
                .andExpect(jsonPath("$.clusters").exists())
                .andExpect(jsonPath("$.clusters.*").isArray());
    }





    @Test
    void shouldExtractTriplesAndReturnJson() throws Exception {
        // Primero subimos el archivo de prueba
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "extract-triples-test.json",
                "application/json",
                Files.readAllBytes(Path.of("src/test/resources/testdata/extract-triples-test.json"))
        );

        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
                .andExpect(status().isOk());

        // Luego invocamos el endpoint de extracción de triples
        mockMvc.perform(post("/api/v1/opensearch/extract-triples")
                        .param("fileName", "extract-triples-test.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].subject").exists())
                .andExpect(jsonPath("$[0].relation").exists())
                .andExpect(jsonPath("$[0].object").exists());
    }



    @Test
    public void testSummarizeBigData() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "bigdata-summary.csv",
                "text/csv",
                ("age,height,weight\n" +
                        "25,170,70\n" +
                        "30,165,60\n" +
                        "28,180,75").getBytes()
        );

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/opensearch/bigdata/summary")
                        .file(mockFile)
                        .param("fileName", "bigdata-summary.csv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").exists())
                .andExpect(jsonPath("$.height.mean").isNumber())
                .andExpect(jsonPath("$.weight.max").isNumber());
    }



    @Test
    void shouldSummarizeAITextFile() throws Exception {
        mockMvc.perform(post("/api/v1/opensearch/ai/summarize")
                        .param("fileName", "ai-summarize_test_input.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.original_length").exists())
                .andExpect(jsonPath("$.summary").isArray())
                .andExpect(jsonPath("$.summary.length()").value(3));
    }


    @Test
    public void testSentimentAnalysisEndpoint() throws Exception {
        String testFileName = "sentiment-analysis_210.json"; // Debe existir en C:/uploaded_files/

        mockMvc.perform(post("/api/v1/opensearch/sentiment-analysis")
                        .param("fileName", testFileName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file").value(testFileName))
                .andExpect(jsonPath("$.documents_indexed").value(210))
                .andExpect(jsonPath("$.sample", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.sample[0].summary_sentiment").exists())
                .andExpect(jsonPath("$.sample[0].original_text").exists());
    }

    @Test
    void testRecognizeEntitiesSuccess() throws Exception {
        String fileName = "entity-recognition_210.json";
        Map<String, List<String>> mockEntities = Map.of(
                "PERSON", List.of("Juan Pérez"),
                "DATE", List.of("2025-05-19")
        );

        when(openSearchService.recognizeEntitiesFromJsonFile("C:/uploaded_files/" + fileName))
                .thenReturn((List<Map<String, Object>>) mockEntities);

        mockMvc.perform(post("/api/v1/opensearch/entity-recognition")
                        .param("fileName", fileName)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PERSON[0]").value("Juan Pérez"))
                .andExpect(jsonPath("$.DATE[0]").value("2025-05-19"));
    }









    /*@Test
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
    }*/


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


    @SpringBootTest
    @AutoConfigureMockMvc
    class EntityRecognitionControllerTest {

        @Autowired
        private MockMvc mockMvc;

        private final String uploadDir = "C:/uploaded_files/";
        private final String testFileName = "entidades_clinicas.txt";

        @BeforeEach
        void setup() throws Exception {
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(uploadDir + testFileName);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("El Dr. Pedro Sánchez trabaja en el Hospital General de Sevilla.\n");
                writer.write("La paciente María González acudió a consulta el 5 de mayo de 2025.\n");
                writer.write("Fue diagnosticada con hipertensión arterial y recomendada a visitar el Centro de Cardiología de Andalucía.\n");
                writer.write("Juan Pérez, otro paciente, fue derivado desde el Hospital Virgen del Rocío a la Clínica Santa Isabel.\n");
                writer.write("La cita de seguimiento fue programada para el 10 de junio de 2025.");
            }
        }

        @Test
        void shouldRecognizeEntitiesInTextFile() throws Exception {
            mockMvc.perform(post("/api/v1/opensearch/entity-recognition")
                            .param("fileName", testFileName))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("PERSON")))
                    .andExpect(content().string(containsString("Pedro Sánchez")))
                    .andExpect(content().string(containsString("Hospital General")))
                    .andExpect(content().string(containsString("2025")));
        }
    }




}
