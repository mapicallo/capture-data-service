package com.mapicallo.capture_data_service.application;

import org.springframework.stereotype.Service;

@Service
public class TextAnonymizerService {

    public String anonymizeTextFromFileContent(String input) {
        String anonymized = input;

        anonymized = anonymized.replaceAll("\\b(Dra?\\.?\\s+\\p{Lu}\\p{L}+)", "[PROFESIONAL]");
        anonymized = anonymized.replaceAll("\\b(\\p{Lu}\\p{L}+\\s+\\p{Lu}\\p{L}+)\\b", "[NOMBRE]");
        anonymized = anonymized.replaceAll("\\b(Hospital|Clínica)\\s+[\\p{L}\\s]+", "[CENTRO_MEDICO]");
        anonymized = anonymized.replaceAll("\\b\\d{2}/\\d{2}/\\d{4}\\b", "[FECHA]");
        anonymized = anonymized.replaceAll("\\b\\d{4}-\\d{2}-\\d{2}\\b", "[FECHA]");

        return anonymized;
    }
}
