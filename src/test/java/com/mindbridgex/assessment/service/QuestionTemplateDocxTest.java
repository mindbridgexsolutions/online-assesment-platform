package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.util.TabularReader;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionTemplateDocxTest {
    @Test
    void wordTemplateIsReadBackByTheImporterWithTheCsvColumns() {
        QuestionImportExportService svc = new QuestionImportExportService(null, null, null, null, null);
        byte[] docx = svc.templateDocx("DS201");
        List<Map<String, String>> rows = TabularReader.read(new MockMultipartFile("file", "t.docx", "application/octet-stream", docx), 100);
        assertEquals(8, rows.size());
        assertEquals("DS201", rows.get(0).get("subject_code"));
        assertEquals("MCQ_SINGLE", rows.get(0).get("type"));
        assertEquals("Venus|Mars|Jupiter|Saturn", rows.get(0).get("options"));
        assertEquals("1,3", rows.get(1).get("correct"));
        assertEquals("Which planet is known as the Red Planet?", rows.get(0).get("question"));
    }
}
