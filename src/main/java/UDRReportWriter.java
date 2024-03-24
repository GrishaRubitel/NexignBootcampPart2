import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Данный класс занимается созданием и заполнением UDR рапортов.
 * Он читает существующие CDR файлы и агрегирует информацию определенных абонентов
 * за определенные промежутки времени.
 */
public class UDRReportWriter {
    public static final String SPLITTER = ", ";
    public static final Path CDR_DIR = Path.of("./CDRDir/");
    public static final String CDR_FILE_TEMPLATE = "CDR_%s.txt";
    public static final String IN_TYPE_SIGN = "01";
    public static final String REPORTS_PATH = "./reports/";
    public static final String UDR_FILE_FORMAT = ".json";
    public static final String MSISDN = "msisdn";
    public static final String TOTAL_TIME = "totalTime";
    public static final String INCOMING_CALL = "incomingCall";
    public static final String OUTCOMING_CALL = "outcomingCall";
    public static final String UDR_FILE_UNDER = "_";

    /**
     * Первая перегрузка метода generateReport().
     * Агрегирует информацию по всем абонентам за все месяцы года.
     * В ходе программы заполняет мапу Map<String, MsisdnStandaloneData>, агрегируя информацию по абонентам.
     * @throws IOException
     */
    public void generateReport() throws IOException {
        String fileName;
        String lastMsisdn = "";
        Map<String, MsisdnStandaloneData> msisdnData = new HashMap<>();
        Gson gson = new Gson();

        for (int i = 1; i <= 12; i++) {
            fileName = getCDRFileName(i);
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] arrS = line.split(SPLITTER);

                    if (!msisdnData.isEmpty() && msisdnData.get(arrS[1]) != null) {
                        msisdnData.get(arrS[1]).extendTotalTime(arrS);
                        continue;
                    }
                    msisdnData.put(arrS[1], new MsisdnStandaloneData(arrS[1]));
                    msisdnData.get(arrS[1]).extendTotalTime(arrS);
                }
            }
            for (var elem : msisdnData.entrySet()) {
                buildJSON(elem.getValue().getMsisdn(), i, elem.getValue().getIncomingTotal(), elem.getValue().getOutcomingTotal(), gson);
            }
        }
        //TODO
        //msisdn переделать в мапу
    }

    /**
     * Вторая прегрузка метода generateReport(msisdn).
     * Будет вызван если в Main был передан массив args с одним единственным значением соответствующим определенной маске regexp.
     * Главное отличие от третьей перегрузки метода generateReport(msisdn, month) - цикл for по каждому месяцу года.
     * @param msisdn Если переданная строка "походит" на номер телефона, будет передан этот элемент.
     * @throws IOException
     */
    public void generateReport(String msisdn) throws IOException {
        for (int i = 1; i <= 12; i++) {
            msisdnDataCollector(msisdn, i);
        }
    }

    /**
     * Третья перегрузка метода generateReport(msisdn, month).
     * Будет вызван если в Main был передан массив args двумя значениями: 1-ое - номер телефона; 2-ое - номер месяца.
     * Этот метод состоит лишь из одной строки из-за того, что если этот метод вызывать 12 раз с
     * разными значениями month - мы получим вторую перегрузку generateReport(msisdn), а значит всё содержимое третьей
     * перегрузки надо вынести в отдельный метод.
     * @param msisdn
     * @param month
     * @throws IOException
     */
    public void generateReport(String msisdn, int month) throws IOException {
        msisdnDataCollector(msisdn, month);
    }

    /**
     * Данный метод вызывается второй и третьей перегрузками generateReport() для создания UDR рапортов
     * @param msisdn
     * @param month
     * @throws IOException
     */
    private void msisdnDataCollector(String msisdn, int month) throws IOException {
        String fileName = getCDRFileName(month);
        int incomingTotal = 0;
        int outcomingTotal = 0;

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] arrS = line.split(SPLITTER);
                if (arrS[1].equals(msisdn)) {
                    if (arrS[0].equals(IN_TYPE_SIGN)) {
                        incomingTotal = incomingTotal + (Integer.parseInt(arrS[3]) - Integer.parseInt(arrS[2]));
                    } else {
                        outcomingTotal = outcomingTotal + (Integer.parseInt(arrS[3]) - Integer.parseInt(arrS[2]));
                    }
                }
            }
        }
        buildJSON(msisdn, month, incomingTotal, outcomingTotal, new Gson());
    }
    private static String getCDRFileName(int month) {
        return String.format(String.valueOf(CDR_DIR.resolve(CDR_FILE_TEMPLATE)), month);
    }

    /**
     * Данный метод занимается "сборкой" строк со знаниями об абонентах и маршализует их в JSON файлы.
     * По файлу на абонента и месяца
     * @param msisdn
     * @param month
     * @param inTotal
     * @param outTotal
     * @param gson
     * @throws IOException
     */
    private void buildJSON(String msisdn, int month, int inTotal, int outTotal, Gson gson) throws IOException {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(MSISDN, msisdn);

        JsonObject incomingCall = new JsonObject();
        incomingCall.addProperty(TOTAL_TIME, parseIntToTime(inTotal));
        jsonObject.add(INCOMING_CALL, incomingCall);

        JsonObject outcomingCall = new JsonObject();
        outcomingCall.addProperty(TOTAL_TIME, parseIntToTime(outTotal));
        jsonObject.add(OUTCOMING_CALL, outcomingCall);

        String json = gson.toJson(jsonObject);

        String filePath = REPORTS_PATH + msisdn + UDR_FILE_UNDER + month + UDR_FILE_FORMAT;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(json);
            System.out.println("JSON is ready " + filePath);
        }
    }
    private String parseIntToTime(int unixTime) {
        int hours = unixTime / 3600;
        int minutes = (unixTime % 3600) / 60;
        int seconds = unixTime % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}

