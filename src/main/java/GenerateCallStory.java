import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Класс отвечает за эмуляцию звонков разных абоненто, как если бы коммутатор на протяжении
 * нескольких месяцев собирал информацию и формировал базу данных звонков.
 * Класс работает с базой данных PostgreSQL (DDL скрипт в root/psql_ddl.sql) и генерирует случайные значения.
 * Этот класс, как было сказано, эмулирует накопление данных по звонкам, в реальности
 * такой процесс, очевидно, занял бы огромное количество времени (1 год), поэтому единственная глобальная
 * функция класса - создать историю, менее пафосно - нагенерировать звонков и занести информацию о них в БД.
 * Никакой связи или обмена данными с другими классами быть не должно.
 */
public class GenerateCallStory {
    public static final String OUT_CALL_TYPE_CODE = "01";
    public static final String IN_CALL_TYPE_CODE = "02";
    private final String HOST;
    private final String USER;
    private final String PASS;
    public GenerateCallStory(String HOST, String USER, String PASS) {
        this.HOST = HOST;
        this.USER = USER;
        this.PASS = PASS;
    }

    /**
     * Метод возвращает номера телефонов из таблицы numbers, но прежде опустошает cdr_holder.
     * Опустошение нужно для независимости каждого запуска программы.
     * Иначе в базе данных будет накапливаться неадекватное количество данных.
     * @param conn Устанавливать соединение с БД - дорого, открываем один раз и передаем методам.
     * @return LinkedList<String> Список номеров СИМ-карт.
     * @throws SQLException
     */
    private LinkedList<String> sqlSelectPhoneNumbers(Connection conn) throws SQLException {

        LinkedList<String> abonents = new LinkedList<>();
        try (Statement trunc = conn.createStatement()) {
            String truncST = "TRUNCATE TABLE cdr_holder";
            trunc.executeUpdate(truncST);
        }

        String queryText = "SELECT * FROM numbers";
        try (Statement st = conn.createStatement();
             ResultSet res = st.executeQuery(queryText);) {

            while (res.next()) {
                String sim = res.getString("sim");
                abonents.add(sim);
            }
        }

        return abonents;
    }

    /**
     * Отправляет в таблицу cdr_holder "пакеты" с историей звонков.
     * INSERT не менее затратная операция, чем установка соединения. "Отправка бэтчей", уменьшает время
     * отправки данных в десятки раз.
     * @param input Список данных для отправки Базе Данных, содержит номер, типа звонка и время звонка.
     * @param conn Устанавливать соединение с БД - дорого, открываем один раз и передаем методам.
     * @throws SQLException
     */
    public void sqlInsertNewArchive(LinkedList<String> input, Connection conn) throws SQLException {
        String insertText = "INSERT INTO cdr_holder (sim, unix_start, unix_end, incoming) VALUES (?, ?, ?, ?)";
        try (PreparedStatement st = conn.prepareStatement(insertText)) {
            for (String row : input) {
                String[] arrFoo = row.split(", ");
                st.setString(1, arrFoo[1]);
                st.setInt(2, Integer.parseInt(arrFoo[2]));
                st.setInt(3, Integer.parseInt(arrFoo[3]));
                st.setString(4, arrFoo[0]);

                st.addBatch();
            }
            st.executeBatch();
        }
    }

    /**
     * Метод эмулирует работу коммутатора по сбору данных по звонкам за каждый месяц 2023-го года.
     * Метод получает список абонентов и для каждого генерирует какое-то количество совершенных звонков (1 - 10),
     * и так 12 раз, по каждому месяцу.
     * @throws SQLException
     */
    public void switchEmulator() throws SQLException {
        int unixStart = 1672531200;
        int unixFinish = 1704067199;
        int unixStep = (unixFinish - unixStart) / 12;

        try (Connection conn = DriverManager.getConnection(HOST, USER, PASS)) {
            LinkedList<String> callHolder = new LinkedList<>();
            LinkedList<String> abonents;
            try {
                abonents = sqlSelectPhoneNumbers(conn);
            } catch(SQLException e) {
                throw e;
            }
            for (int i = 1; i <= 12; i++) {
                for (String elem : abonents) {
                    for (int t = 0; t < ThreadLocalRandom.current().nextInt(1, 11); t++) {
                        int start = randUnixCallStart(unixStart, unixStep);
                        String temp = String.format("%s, %s, %d, %d", inOrOut(), elem, start, randUnixCallFinish(start));
                        callHolder.add(temp);
                    }
                    if (callHolder.size() >= 90) {
                        sqlInsertNewArchive(callHolder, conn);
                        callHolder.clear();
                    }
                }
                unixStart += unixStep;
            }
            sqlInsertNewArchive(callHolder, conn);
        }
    }

    private String inOrOut() {
        if (new Random().nextBoolean()) {
            return OUT_CALL_TYPE_CODE;
        } else {
            return IN_CALL_TYPE_CODE;
        }
    }

    private int randUnixCallStart(int start, int step) {
        return ThreadLocalRandom.current().nextInt(start, start + step + 1);
    }
    private int randUnixCallFinish(int start) {
        return ThreadLocalRandom.current().nextInt(start, start + 301);
    }
}
