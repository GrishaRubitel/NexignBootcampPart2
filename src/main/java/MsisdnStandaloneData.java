/**
 * Класс коллектор - содержит в себе информацию об абоненте.
 * Используется в первой перегрузке generateReport().
 */
public class MsisdnStandaloneData {
    public static final String IN_TYPE_SIGN = "01";
    private final String msisdn;
    private int incomingTotal;
    private int outcomingTotal;
    public MsisdnStandaloneData(String msisdn) {
        this.msisdn = msisdn;
        incomingTotal = 0;
        outcomingTotal = 0;
    }
    public void extendIncoming(int val) {
        incomingTotal += val;
    }
    public void extendOutcoming(int val) {
        outcomingTotal += val;
    }

    public void extendTotalTime(String[] arrS) {
        if (arrS[0].equals(IN_TYPE_SIGN)) {
            extendIncoming(Integer.parseInt(arrS[3]) - Integer.parseInt(arrS[2]));
        } else {
            extendOutcoming(Integer.parseInt(arrS[3]) - Integer.parseInt(arrS[2]));
        }
    }

    //TODO
    //Убрать парсинг (опцонально)

    public String getMsisdn() {
        return msisdn;
    }

    public int getIncomingTotal() {
        return incomingTotal;
    }

    public int getOutcomingTotal() {
        return outcomingTotal;
    }
}
