# Nexign Bootcamp. Part II

## Виниченко Даниил Николаевич

## Установка

Данный проект реализован с поддержкой Maven. Для установки зависимостей и драйверов требуется прописать следующие команды в cmd/PS/terminal/zsh внутри папки с кодом:

```jsx
mvn install
mvn package
```

Также эти операции можно произвести при помощи интерфейса IDE. Гайд по установке Maven: [https://maven.apache.org/install.html](https://maven.apache.org/install.html)

```
P.s. bootcampPartII-1.0-SNAPSHOT.jar находится в папке target
```

После “компиляции” проект можно запустить как из используемой IDE (но с нюансом, о нём позже), так и из консоли:

```jsx
java -jar .\target\bootcampPartII-1.0-SNAPSHOT.jar [phone_number] [month_num]
```

---

Программа, как сказано в условии, должна работать с базой данных. В данном случае используется PostgreSQL. Ниже приведён DDL код для создания необходимой базы

```sql
---DDL for PostgreSQL DB from 1st task
create table numbers (
	sim varchar not null,
	constraint numbers_pk primary key (sim)
);

create table cdr_holder (
	id serial4 not null,
	sim varchar null,
	unix_start int4 null,
	unix_end int4 null,
	incoming varchar null,
	constraint cdr_holder_pk primary key (id)
);

insert into numbers values (7968969935), (74571938267), (71364416478),
						   (7747873230), (74982406633), (787845253770),
						   (74374224157), (75326984736), (76168793160),
						   (79298674093);

```

---

## Преамбула

Все звонки, совершенные абонентом сотового оператора, фиксируются в CDR файлы, которые собираются на коммутаторах. Когда абонент находится в роуминге за процесс сбора его данных отвечает обслуживающая сеть абонента. Для стандартизации данных между разными операторами международная ассоциация GSMA ввела стандарт BCE. Согласно ему, данные с CDR должны агрегировать в единый отчет UDR, который впоследствии передается оператору, обслуживающему абонента в домашней сети. На основе этого отчета, домашний оператор выставляет абоненту счет.
В рамках задания, CDR будут содержать записи следующего вида:

- тип вызова (01 - исходящие, 02 - входящие);
- номер абонента;
- дата и время начала звонка (Unix time);
- дата и время окончания звонка;
- разделитель данных – запятая;
- разделитель записей – перенос строки;
- данные обязательно формируются в хронологическом порядке;
- В рамках задания CDR может быть обычным txt;

Вот пример фрагмента CDR:

```sql
02, 7747873230, 1673410398, 1673410643

01, 74982406633, 1674710545, 1674710781
```

UDR будет агрегировать данные по абонентам и суммировать длительность вызовов разного типа. 

Пример UDR объекта для абонента `7747873230`

```json
{
	"msisdn":"7747873230",
	"incomingCall": 
	{
		"totalTime":"00:03:06"
	},
	"outcomingCall":
	{
		"totalTime":"00:11:47"
	}
}
```

---

## Задача 1

Напишите сервис, эмулирующий работу коммутатора, т.е. генерирующий CDR файлы.

Условия:

1.  1 CDR = 1 месяц. Тарифицируемый период в рамках задания - 1 год;
2. Данные в CDR идут не по порядку, т.е. записи по одному абоненту
могут быть в разных частях файла;
3. Количество и длительность звонков определяется случайным
образом;
4. Установленный список абонентов (не менее 10) хранится в
локальной БД (h2);
5. После генерации CDR, данные о транзакциях пользователя
помещаются в соседнюю таблицу этой БД.

---

## Ход работы

### Вход в программу

Перед запуском программы следует обновить определенные константы в файле Main. Это нужно для подключения к нужной базе данных

```java
public static final String PSQL_HOST = "jdbc:postgresql://localhost:5432/postgres";
public static final String PSQL_USER = "postgres";
public static final String PSQL_PASS = "dvpsql";
```

---

Программа, условно, делится на две части, где первая, в свою очередь, еще на две:

1. Генерация CDR файлов:
    1. Эмуляция годового сбора информации о звонках и запись её в базу данных
    2. Извлечение информации из базы данных, сортировка и запись полученных данных по соответствующим месяцу CDR файлам
2. Создание UDR файлов

---

---

### Генерация истории звонков

Первым методом, после входа в программу, является

```java
GenerateCallStory abonentHolder = new GenerateCallStory(PSQL_HOST,PSQL_USER,PSQL_PASS);
abonentHolder.switchEmulator();
```

В новой, только что созданной, базе данных хранятся лишь номера телефонов, которые будут извлечены данным методом. После извлечения номеров будет создан цикл for на 12 итераций - по итерации на месяц. Внутри этого цикла будет вызван ещё один цикл для прохода по каждому номеру телефона. А потом будет создан ещё один цикл со случайным количество итераций от 1 до 10

```java
for (int i = 1; i <= 12; i++) {
                for (String elem : abonents) {
                    for (int t = 0; t < ThreadLocalRandom.current().nextInt(1, 11); t++) 
```

Этой конструкцией мы добьемся того, что каждый абонент будет иметь хотя бы по одной записи в каждый месяц года.

Дальше по ходу каждому номеру телефона будут “дописаны” недостающие поля для записи их в CDR файлы: тип звонка (входящий или исходящий), unix time начала и окончания звонка - всего 3 поля.

 Согласно условию, полученные на этом этапе данные нужно занести в базу данных. Это нужно для, как раз, эмуляции накопления данных о звонках

```java
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
```

Данный код отправляет в таблицу cdr_holder "пакеты" с историей звонков. Операция INSERT крайне затратная, поэтому для оптимизация DML “постов” используются batch’и, они позволяют выполнить много последовательных запросов за один раз, таким образом мы сократим время выполнения программы в десятки раз

Описанные батчи отправляются при определенном условии. Переменная input - это связный список, и когда его размер достигает 90 элементов, срабатывает код С4. Такое условие необходимо, так как оптимальным объемом батча является диапазон в 50-100 элементов. Также этот код сработает при выходе из циклов, описанных в С3, когда процесс составления истории звонков будет окончен

Когда работа метода `**switchEmulator()`** завершится, он никуда не передаст никакую информацию. Так сделано для более точной эмуляции - CDR генератор должен брать “накопленную” информацию из базы данных, а не из метода, который после передачи нигде не сохранит свои данные.

Таким образом мы получили базу данных, полную материала для CDR файлов. В файле psql_sql.sql записаны определенные SQL запросы, для просмотра или проверки информации

---

### Создание CDR

Следующей функцией в “Мэйне” окажется 

```java
CDRGenerator cdrGen = new CDRGenerator(PSQL_HOST, PSQL_USER, PSQL_PASS);
cdrGen.cdrCreator();
```

Этот метод читает из базы данных историю звонков по каждому месяцу и заполняет соответствующий CDR файл 

```java
for (int i = 1; i <= 12; i++) {
                String queryText = "SELECT * FROM cdr_holder WHERE EXTRACT(MONTH FROM 
                TO_TIMESTAMP(unix_start)) = " + i;
```

Чтение/запись в/из любого файла производится при помощи буфера. Таким образом мы получаем 12 CDR файлов с извлеченными из базы данных знаниями

---

## Задача 2

Данные полученные от CDR сервиса передать в сервис генерации UDR. Агрегировать данные по каждому абоненту в отчет.
Условия:

1. Данные можно брать только из CDR файла. БД с описанием транзакций – тестовая, и доступа к ней, в рамках задания нет.
2. Сгенерированные объекты отчета разместить в /reports. Шаблон имени: номер_месяц.json (79876543221_1.json);
3. Класс генератора должен содержать методы:
    1. generateReport() – сохраняет все отчеты и выводит в консоль таблицу со всеми абонентами и итоговым временем звонков по всему тарифицируемому периоду каждого абонента;
    2. generateReport(msisdn) – сохраняет все отчеты и выводит в консоль таблицу по одному абоненту и его итоговому времени звонков в каждом месяце;
    3. generateReport(msisdn, month) – сохраняет отчет и выводит в консоль таблицу по одному абоненту и его итоговому времени звонков в указанном месяце.

---

## Ход работы

### Создание UDR файлов

За создание UDR файлов отвечает метод **`generateReport()`** класса **`UDRReportWrite.`** Но есть один нюанс - у этого метода 3 перегрузки

```java
generateReport()
generateReport(msisdn)
generateReport(msisdn, month)
```

Нюанс заключается в следующем - переменные msisdn и month берутся из массива строк String[] args. Однако с руки занести значения в этот массив мы не можем, в коде не предусмотрен ввод в консоль, поэтому при запуске через IDE кнопкой “Пуск” по умолчанию будет запущен первый метод generateReport(), так как массив args - пустой.

Как было сказано в начале документа, мы можем запустить программу при помощи консольной команды:

```java
java -jar .\target\bootcampPartII-1.0-SNAPSHOT.jar [phone_number] [month_num]
```

Именно при помощи этой команды мы можем передать значения в args. По мере выполнения программа дойдёт до определённого обработчик массива args

```java
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
```

Как видно из С9, если args пустой, будет вызвана первая “нулёвая” перегрузка generateReport(), которая не требует значения в args. Данный метод будет проходиться по всем CDR и агрегировать информацию по всем абонентам

Если args имеет длину 1 и один единственный элемент совпадает с regexp маской **`"^7.{7,}$",`** то есть походит на номер телефона (с кодом РФ), будет вызвана вторая перегрузка generateReport(msisdn), которая обработает лишь абонента с переданным номером телефона. Метод также будет проходиться по всем CDR файлам, но будет агрегировать информацию лишь по одному конкретному абоненту.

Если массив состоит из двух или более элементов, выполняется условие совпадения с маской **`"^7.{7,}$"` ,** и второй элемент находится в диапазоне от 1го до 12ти, то будет выполнена третья перегрузка generateReport(msisdn, month). Алгоритм точно такой же, как и у второй перегрузки, только по одному CDR/месяцу

---

Чтение CDR, как было сказано выше, производится через буфер, после чего, в общем случае, отправляются отдельному методу для генерации JSON и записи его в UDR файл

```java
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
```

Распаршенные строки CDR отправляются в **`buildJSON` ,** название метода говорит само за себя. Вместе с данными в метод передается объект Gson, который будет заниматься маршализацией строки в JSON. Этот объект создаётся “единожды” с целью экономии ресурсов, так как его создание - дело затратное. Такая же ситуация, как и с соединениями с базами данных и отправкой DML постов.

```java
JsonObject jsonObject = new JsonObject();
jsonObject.addProperty(MSISDN, msisdn);

JsonObject incomingCall = new JsonObject();
incomingCall.addProperty(TOTAL_TIME, parseIntToTime(inTotal));
jsonObject.add(INCOMING_CALL, incomingCall);

JsonObject outcomingCall = new JsonObject();
outcomingCall.addProperty(TOTAL_TIME, parseIntToTime(outTotal));
jsonObject.add(OUTCOMING_CALL, outcomingCall);

String json = gson.toJson(jsonObject);
```

В С10 показано решение, общее для generateReport(msisdn) и generateReport(msisdn, month), так как разница между ними в одном цикле for - первую перегрузку можно выполнить, если 12 раз прогнать третью.

Для перегрузки generateReport() всё сложнее, там не один абонент, а сильно больше, как минимум десять:

```java
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
```

Поэтому здесь, для составления статистики, мало создать двух переменных **`incomingTotal`** и **`outcomingTotal` ,** как в С10. Здесь информация о каждом абоненте сохраняется в объекты класса `**MsisdnStandaloneData`** 

```java
private final String msisdn;
private int incomingTotal;
private int outcomingTotal;
```

А эти объекты, в свою очередь, хранятся в мапе

```java
Map<String, MsisdnStandaloneData> msisdnData = new HashMap<>();
```

После чего эта мапа парсится и отправляется в метод buildJSON (C12)

---
---

**P.s.** Про вывод сводки в консоль я прочитал лишь на стадии написания md документа и не успел это реализовать, каюсь и прошу прощения

```
1. generateReport() – сохраняет все отчеты и выводит в консоль таблицу со всеми абонентами и итоговым временем звонков по всему тарифицируемому периоду каждого абонента;
2. generateReport(msisdn) – сохраняет все отчеты и выводит в консоль таблицу по одному абоненту и его итоговому времени звонков в каждом месяце;
3. generateReport(msisdn, month) – сохраняет отчет и выводит в консоль таблицу по одному абоненту и его итоговому времени звонков в указанном месяце.
```

**P.p.s** Я занял первое место и выиграл мышку в викторине Nexign на Карьерном Форуме в Политехе Петра. Надеюсь это достижение перекроет предыдущий пункт 👉🏼👈🏼

---
---

## Тестирование

### Тест генерации CDR (`CDRGeneratorTest`)

```java
for (int i = 1; i <= 12; i++) {
    String queryText = "SELECT * FROM cdr_holder WHERE" 
											 "EXTRACT(MONTH FROM TO_TIMESTAMP(unix_start)) = " + i;
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
```

Данный тест предназначен для сверки корректности заполнения CDR файлов. Тест имеет 2 метода `**getSQLList`** и `**getCDRList`** , которые возвращают по своему связному списку, первый метод - список записей из таблицы БД, второй - список записей отдельного CDR файла. 

Алгоритм сравнения: берётся элемент из списка `**sqlElem**` и ищется в списке `**cdrElem`** , если элемент находится, то он удаляется из второго списка. Если после выхода из первого цикла список `**cdrElem`**  не становится пустым, то тест считается заваленным

Таким образом можно проверить механизм записи данных в CDR. Например, можно не закрыть какой-нибудь reader, после чего данные будет записаны не до конца (пропуск строк и полей). Проверено на себе!

### Тест создания истории звонков (`GenerateCallStoryTest`)

```java
String in1 = "01, 7968969935, 1000000000, 1000000001";
String in2 = "02, 7968969935, 1000000111, 1000000111";
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
```

В данном тесте в таблицу с историей звонков заносятся две тестовые записи при помощи метода `**generateCallStory.sqlInsertNewArchive(input, conn)`** . После чего эти записи ищутся в таблице, и если тестовые значения идентичны тем, что находятся в базе данных, то тест считается успешным.

Тестовые данные, по итогам теста, удаляются из базы данных, поэтому случайно сагрегировать их не получится

---

### Тест генерации UDR (`UDRReportWriterTest`)

```java
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
```

Данный тест позволяет автоматически сравнить значения из базы со значениями из UDR файла.

Программа делает специальный SQL запрос на сервер, который возвращает только сумму входящих/исходящих (при наличии) конкретного абонента за конкретный месяц. В тесте присутствует нетипичный (как по мне) механизм подтверждения вхождения - от обратного. Изначально подразумевается, что данные из БД и UDR идентичны. Однако если по ходу теста оказывается, что это не так, то тесть проваливается.

Механизм сделан именно таким образом, потому что абонент мог, например, никому не звонить, а только принимать звонки, получается, что в базе данных не будет записей об исходящих. Но алгоритму записи UDR на это всё равно, на месте входящих должно быть что-то написано. И это что-то - **`00:00:00`.** Именно с целью избегания ложного нетестирования, исход теста изначально истинен. 

---

---
