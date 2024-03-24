import java.io.File;
import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class Main {

    public static final String REPORTS_DIR = "reports";
    public static final String CDR_DIR = "CDRDir";
    public static final String PSQL_HOST = "jdbc:postgresql://localhost:5432/postgres";
    public static final String PSQL_USER = "postgres";
    public static final String PSQL_PASS = "dvpsql";

    public static void main(String[] args) {
        createDirs(REPORTS_DIR);
        createDirs(CDR_DIR);

        long stopWatch = System.nanoTime();

        GenerateCallStory abonentHolder = new GenerateCallStory(PSQL_HOST, PSQL_USER, PSQL_PASS);
        CDRGenerator cdrGen = new CDRGenerator(PSQL_HOST, PSQL_USER, PSQL_PASS);

        try {
            abonentHolder.switchEmulator();
        } catch (SQLException e) {
            System.out.println("SQL Exception when generating call story: " + e);
        }

        try {
            cdrGen.cdrCreator();
        } catch (SQLException e) {
            System.out.println("SQL Exception when generating CDR files: " + e);
        } catch (IOException e) {
            System.out.println("IO Exception when generating CDR files: " + e);
        }


        String meth = "Nothing";
        UDRReportWriter udrGen = new UDRReportWriter();
        if (args.length == 0) {
            try {
                udrGen.generateReport();
                meth = "Simple generateReport";
            } catch (IOException e) {
                System.out.println(e);
            }
        } else if (args.length == 1 & args[0].matches("^7.{7,}$")) {
            try {
                udrGen.generateReport(args[0]);
                meth = "generateReport with number";
            } catch (IOException e) {
                System.out.println(e);
            }
        } else if (args.length >= 2 & args[0].matches("^7.{7,}$") &
                Integer.parseInt(args[1]) >= 1 & Integer.parseInt(args[1]) <= 12) {
            try {
                udrGen.generateReport(args[0], Integer.parseInt(args[1]));
                meth = "generateReport with number and month";
            } catch (IOException e) {
                System.out.println(e);
            }
        }

        long stopWatchFin = System.nanoTime();
        System.out.println(meth + " executed in " + (stopWatchFin - stopWatch) / 1000000 + " ms");
    }

    private static void createDirs(String name) {
        File nf = new File(name);
        if (!nf.exists()) {
            nf.mkdirs();
        }
    }
}