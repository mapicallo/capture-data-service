package com.mapicallo.capture_data_service.application;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio para anonimizar textos clínicos en español usando Stanford CoreNLP.
 * Sustituye entidades personales como nombres, fechas, lugares y organizaciones
 * por etiquetas genéricas sin alterar el resto del contenido.
 */
@Service
public class TextAnonymizerService {

    private final StanfordCoreNLP pipeline;

    public TextAnonymizerService() {
        // Configuración del pipeline de CoreNLP con los anotadores necesarios para NER
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        props.setProperty("tokenize.language", "es"); // lenguaje español
        props.setProperty("ner.applyFineGrained", "false"); // desactiva etiquetas demasiado específicas
        pipeline = new StanfordCoreNLP(props);
    }

    public String anonymizeTextFromFileContent(String input) {
        // Anotamos el texto con el pipeline configurado
        Annotation document = new Annotation(input);
        pipeline.annotate(document);

        StringBuilder anonymizedText = new StringBuilder();

        // Recorremos cada frase del texto
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            // Recorremos cada palabra/token de la frase
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.originalText();
                String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

                // Sustituimos entidades reconocidas por etiquetas genéricas
                switch (ner) {
                    case "PERSON" -> anonymizedText.append("[NOMBRE]");
                    case "ORGANIZATION" -> anonymizedText.append("[ORGANIZACION]");
                    case "LOCATION" -> anonymizedText.append("[LUGAR]");
                    case "DATE" -> anonymizedText.append("[FECHA]");
                    default -> anonymizedText.append(word);
                }
                anonymizedText.append(" ");
            }
        }

        return anonymizedText.toString().trim();
    }
}

