package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.domain.QuestionType;
import com.mindbridgex.assessment.entity.Question;
import com.mindbridgex.assessment.entity.QuestionOption;
import com.mindbridgex.assessment.util.ContentHasher;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Computes the duplicate-detection fingerprints of a question: a text/options hash and, for pictures, an image hash. */
@Component
public class QuestionHasher {
    private final FileStorageService storage;

    public record Hashes(String content, String image) {
    }

    public QuestionHasher(FileStorageService storage) {
        this.storage = storage;
    }

    public Hashes compute(Question q) {
        List<String> tokens = new ArrayList<>();
        List<String> imageHashes = new ArrayList<>();
        String qImg = storage.sha256(q.getImagePath());
        if (qImg != null) {
            imageHashes.add("Q:" + qImg);
        }
        for (QuestionOption o : q.getOptions()) {
            String oImg = storage.sha256(o.getImagePath());
            String token = o.getText() == null ? "" : o.getText();
            if (q.getType() == QuestionType.MATCHING) {
                token += "=" + (o.getMatchText() == null ? "" : o.getMatchText());
            }
            if (q.getType() == QuestionType.TRUE_FALSE && o.isCorrect()) {
                token += " (answer)";
            }
            tokens.add(token);
            if (oImg != null) {
                imageHashes.add("O:" + oImg);
            }
        }
        if (q.getType() == QuestionType.NUMERIC && q.getNumericAnswer() != null) {
            tokens.add("=" + q.getNumericAnswer().stripTrailingZeros().toPlainString());
        }
        String content = ContentHasher.contentHash(q.getType().name(), q.getText(), tokens);
        String image = null;
        if (!imageHashes.isEmpty()) {
            image = ContentHasher.sha256(String.join("|", imageHashes));
        }
        return new Hashes(content, image);
    }
}
