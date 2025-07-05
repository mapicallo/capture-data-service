package com.mapicallo.capture_data_service.application;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TextAnonymizerService {

    private final StanfordCoreNLP pipeline;

    public TextAnonymizerService() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        props.setProperty("tokenize.language", "es"); // español
        props.setProperty("ner.applyFineGrained", "false");
        pipeline = new StanfordCoreNLP(props);
    }

    public String anonymizeTextFromFileContent(String input) {
        Annotation document = new Annotation(input);
        pipeline.annotate(document);

        StringBuilder anonymizedText = new StringBuilder();
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

        for (CoreMap sentence : sentences) {
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.originalText();
                String ner = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

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
