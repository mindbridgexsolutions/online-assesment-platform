package com.mindbridgex.assessment.service;

import com.mindbridgex.assessment.config.AppProperties;
import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.util.TabularReader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadTypesTest {

    @TempDir Path dir;
    FileStorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        AppProperties props = new AppProperties();
        props.getStorage().setDir(dir.toString());
        storage = new FileStorageService(props);
    }

    private static byte[] docx(String... rows) throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable t = d.createTable(rows.length, 2);
            for (int r = 0; r < rows.length; r++) {
                String[] cells = rows[r].split("\\|");
                t.getRow(r).getCell(0).setText(cells[0]);
                t.getRow(r).getCell(1).setText(cells[1]);
            }
            d.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void acceptsPdfByItsBytes() {
        String name = storage.storeImage(new MockMultipartFile("image", "x.bin", "application/octet-stream",
                "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII)));
        assertThat(name).endsWith(".pdf");
        assertThat(storage.load(name).exists()).isTrue();
    }

    @Test
    void acceptsRealWordDocument() throws Exception {
        String name = storage.storeImage(new MockMultipartFile("image", "a.docx", "application/octet-stream", docx("a|b")));
        assertThat(name).endsWith(".docx");
    }

    @Test
    void rejectsPlainZipRenamedToDocx() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("hello.txt"));
            z.write(1);
            z.closeEntry();
        }
        assertThatThrownBy(() -> storage.storeImage(new MockMultipartFile("image", "fake.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", out.toByteArray())))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejectsExecutablesWhateverTheyAreCalled() {
        assertThatThrownBy(() -> storage.storeImage(new MockMultipartFile("image", "evil.pdf", "application/pdf", "MZ\u0090 binary".getBytes(StandardCharsets.ISO_8859_1))))
                .isInstanceOf(AppException.class);
    }

    @Test
    void readsFirstTableOfWordDocumentAsRows() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "users.docx", "application/octet-stream", docx("Full Name|Email", "Asha|asha@example.com", "Ravi|ravi@example.com"));
        List<Map<String, String>> rows = TabularReader.read(f, 100);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("full_name", "Asha").containsEntry("email", "asha@example.com");
    }

    @Test
    void pdfImportGivesAHelpfulMessage() {
        MockMultipartFile f = new MockMultipartFile("file", "q.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> TabularReader.read(f, 10)).isInstanceOf(AppException.class).hasMessageContaining("Word");
    }
}
