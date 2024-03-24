import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;

/**
 * Данный класс занимается агрегированием данных по звонкам различных пользователей.
 * В отличие от GenerateCallStory, этот класс уже можно применять в реальном продакшене, так как ему не надо ничего
 * генерировать, а лишь записать в файлы уже имеющуюся в базе данных информацию.
 */
public class CDRGenerator {
    public static final Path CDR_DIR = Path.of("./CDRDir/");
    public static final String CDR_FILE_TEMPLATE = "CDR_%s.txt";
    public static final String INCOMING = "incoming";
    public static final String SIM = "sim";
    public static final String UNIX_START = "unix_start";
    public static final String UNIX_END = "unix_end";
    private final String HOST;
    private final String USER;
    private final String PASS;
    public CDRGenerator(String HOST, String USER, String PASS) {
        this.HOST = HOST;
        this.USER = USER;
        this.PASS = PASS;
    }

    /**
     * Данный метод делает SQL запрос к базе данных с фильтром на каждый месяц года.
     * Из полученных записей программа формирует строки и записывает их в соответствующие месяцу CDR файлы.
     * @throws SQLException
     * @throws IOException
     */
    public void cdrCreator() throws SQLException, IOException {
        String lastNum = "";
        try (Connection conn = DriverManager.getConnection(HOST, USER, PASS);
             Statement st = conn.createStatement()) {

            for (int i = 1; i <= 12; i++) {
                String queryText = "SELECT * FROM cdr_holder WHERE EXTRACT(MONTH FROM TO_TIMESTAMP(unix_start)) = " + i;
                try (ResultSet res = st.executeQuery(queryText);
                     BufferedWriter fw = new BufferedWriter(new FileWriter(String.format(String.valueOf(CDR_DIR.resolve(CDR_FILE_TEMPLATE)), i)))) {

                    while (res.next()) {
                        String incoming = res.getString(INCOMING);
                        String sim = res.getString(SIM);
                        int unix_start = res.getInt(UNIX_START);
                        int unix_end = res.getInt(UNIX_END);

                        if (!sim.equals(lastNum)) {
                            lastNum = sim;
                            fw.write(System.lineSeparator());
                        }
                        fw.write(String.format("%s, %s, %d, %d\n",
                                incoming, sim, unix_start, unix_end));
                    }
                }
            }
        }
    }
}
