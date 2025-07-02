package cz.nocard.android;

public class NfcExportServiceState {

    private boolean isEnabled;
    private long transactionId;

    public NfcExportServiceState() {
        isEnabled = false;
        newTransactionId();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public void newTransactionId() {
        transactionId = System.currentTimeMillis();
    }

    public long getTransactionId() {
        return transactionId;
    }
}
