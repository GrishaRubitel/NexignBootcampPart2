import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

public class CDRGeneratorTest {
    public static final Path CDR_DIR = Path.of("./CDRDir/");
    public static final String CDR_FILE_TEMPLATE = "CDR_%s.txt";
    public static final String INCOMING = "incoming";
    public static final String SIM = "sim";
    public static final String UNIX_START = "unix_start";
    public static final String UNIX_END = "unix_end";
    private static final String HOST = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "dvpsql";
    public static final String SPL = ", ";
    @Test
    void testcdrCreator() throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection(HOST, USER, PASS);
             Statement st = conn.createStatement()) {

            for (int i = 1; i <= 12; i++) {
                String queryText = "SELECT * FROM cdr_holder WHERE EXTRACT(MONTH FROM TO_TIMESTAMP(unix_start)) = " + i;
                String fileName = getCDRFileName(i);
                LinkedList<String> sqlRows = getSQLList(st, queryText);
                LinkedList<String> cdrRows = getCDRList(fileName);

                for (var sqlElem : sqlRows) {
                    for (var cdrElem : cdrRows) {
                        if (sqlElem.equals(cdrElem)) {
                            cdrRows.remove(cdrElem);
                            break;
                        }
                    }
                }
                if (!cdrRows.isEmpty()) {
                    fail();
                }
            }
        }
        System.out.println("Content of every CDR File is correct!");
    }
    private static String getCDRFileName(int month) {
        return String.format(String.valueOf(CDR_DIR.resolve(CDR_FILE_TEMPLATE)), month);
    }
    private LinkedList<String> getCDRList(String fileName) throws IOException {
        LinkedList<String> cdrRows = new LinkedList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (!line.isEmpty()) {
                    cdrRows.add(line);
                }
            }
        }
        return cdrRows;
    }

    private LinkedList<String> getSQLList(Statement st, String queryText) throws SQLException {
        LinkedList<String> sqlRows = new LinkedList<>();
        try (ResultSet res = st.executeQuery(queryText)) {
            while (res.next()) {
                String incoming = res.getString(INCOMING);
                String sim = res.getString(SIM);
                int unix_start = res.getInt(UNIX_START);
                int unix_end = res.getInt(UNIX_END);

                sqlRows.add(incoming + SPL + sim + SPL + unix_start + SPL + unix_end);
            }
        }
        return sqlRows;
    }
}
