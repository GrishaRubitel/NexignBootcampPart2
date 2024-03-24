import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UDRReportWriterTest {
    private static final String HOST = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "dvpsql";
    public static final String OUT_CALL_TYPE_CODE = "01";
    public static final String MSISDN = "7747873230";
    public static final int MONTH = 4;
    public static final String INCOMING = "incoming";
    public static final String SUM = "sum";
    public static final String REPORTS = "./reports/";
    public static final String UDR_FILE_FORMAT = ".json";
    public static final String UDR_FILE_UNDER = "_";
    public static final String TOTAL_TIME = "totalTime";
    public static final String INCOMING_CALL = "incomingCall";
    public static final String OUTCOMING_CALL = "outcomingCall";

    @Test
    void testMsisdnDataCollector() throws SQLException {

        String fileName = REPORTS + MSISDN + UDR_FILE_UNDER + MONTH + UDR_FILE_FORMAT;

        boolean inB = true;
        boolean outB = true;

        UDRReportWriter udrGen = new UDRReportWriter();
        try {
            udrGen.generateReport(MSISDN, MONTH);
        } catch (IOException e) {
            System.out.println("IOException when testing msisdnDataCollector: " + e);
        }

        try (Connection conn = DriverManager.getConnection(HOST, USER, PASS);
             PreparedStatement st = conn.prepareStatement("SELECT incoming, SUM(smf)\n" +
                                             "FROM (\n" +
                                             "\tSELECT *, (unix_end - unix_start) AS smf\n" +
                                             "\tFROM cdr_holder ch\n" +
                                             "\tWHERE EXTRACT(MONTH FROM TO_TIMESTAMP(unix_start)) = ? AND sim = ?\n" +
                                             ") sub\n" +
                                             "GROUP BY (incoming);")) {
            st.setInt(1, MONTH);
            st.setString(2, MSISDN);
            ResultSet resultSet = st.executeQuery();

            while (resultSet.next()) {
                String type = resultSet.getString(INCOMING);
                long amount = resultSet.getLong(SUM);

                try {
                    if (!compareWithUDRData(fileName, type, amount)) {
                        if (type.equals(OUT_CALL_TYPE_CODE) && amount != 0) {
                            inB = false;
                            System.out.println("Incoming total is not correct");
                        } else {
                            outB = false;
                            System.out.println("Outcoming total is not correct");
                        }
                    }
                } catch (IOException e) {
                    System.out.println("IO Exception when testing msisdnDataCollector: ");
                }
            }
        }
        assertTrue(inB);
        assertTrue(outB);
        System.out.println("Both total time are correct");
    }

    private boolean compareWithUDRData(String fileName, String type, long value) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            String currTime = getString(type, stringBuilder);
            long hours = value / 3600;
            long minutes = (value % 3600) / 60;
            long seconds = value % 60;

            return String.format("%02d:%02d:%02d", hours, minutes, seconds).equals(currTime);
        }
    }

    private String getString(String type, StringBuilder stringBuilder) {
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = jsonParser.parse(stringBuilder.toString()).getAsJsonObject();

        String currTime;
        if (type.equals(OUT_CALL_TYPE_CODE)) {
            JsonObject incomingCall = jsonObject.getAsJsonObject(INCOMING_CALL);
            currTime = incomingCall.get(TOTAL_TIME).getAsString();
        } else {
            JsonObject outcomingCall = jsonObject.getAsJsonObject(OUTCOMING_CALL);
            currTime = outcomingCall.get(TOTAL_TIME).getAsString();
        }
        return currTime;
    }
}
