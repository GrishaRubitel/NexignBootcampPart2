import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.LinkedList;

public class GenerateCallStoryTest {
    private static final String HOST = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "dvpsql";
    public static final String INCOMING = "incoming";
    public static final String UNIX_START = "unix_start";
    public static final String MSISDN = "sim";
    public static final String UNIX_END = "unix_end";

    @Test
    void testSqlInsertNewArchive() {
        GenerateCallStory generateCallStory = new GenerateCallStory(HOST, USER, PASS);
        LinkedList<String> input = new LinkedList<>();
        String in1 = "01, 7968969935, 1000000000, 1000000001";
        String in2 = "02, 7968969935, 1000000111, 1000000111";
        boolean in1B = false;
        boolean in2B = false;
        input.add(in1);
        input.add(in2);

        try (Connection conn = DriverManager.getConnection(HOST, USER, PASS)) {
            generateCallStory.sqlInsertNewArchive(input, conn);

            try (Statement statement = conn.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT sim, unix_start, unix_end, incoming FROM cdr_holder ORDER BY id desc")) {
                while ((!in1B || !in2B) && resultSet.next()) {
                    String row = resultSet.getString(INCOMING) + ", " + resultSet.getString(MSISDN)
                            + ", " + resultSet.getLong(UNIX_START) + ", " + resultSet.getLong(UNIX_END);
                    if (row.equals(in1) && !in1B) {
                        in1B = true;
                    } else if (row.equals(in2) && !in2B) {
                        in2B = true;
                    }
                }
            }

            deleteTestValues(conn, in1);
            deleteTestValues(conn, in2);
            assertTrue(in1B);
            assertTrue(in2B);

        } catch (SQLException e) {
            fail("SQL Exception occurred: " + e.getMessage());
        }
    }

    private static void deleteTestValues(Connection connection, String input) throws SQLException {
        String[] val = input.split(", ");

        String req = "DELETE FROM cdr_holder WHERE incoming = ? AND sim = ? AND unix_start = ? AND unix_end = ?";
        try (PreparedStatement st = connection.prepareStatement(req)) {
            st.setString(1, val[0]);
            st.setString(2, val[1]);
            st.setInt(3, Integer.parseInt(val[2]));
            st.setInt(4, Integer.parseInt(val[3]));

            int numOfDeleted = st.executeUpdate();
            System.out.println("String deleted: " + input + " (" + numOfDeleted + " times)");
        }
    }
}