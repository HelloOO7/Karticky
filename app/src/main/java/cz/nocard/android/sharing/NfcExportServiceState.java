package cz.nocard.android.sharing;

import java.util.ArrayList;
import java.util.List;

import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;

public class NfcExportServiceState {

    private boolean isEnabled;
    private List<PersonalCard> exportCardFilter;

    public NfcExportServiceState() {
        isEnabled = false;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public void setExportCardFilter(List<PersonalCard> cards) {
        this.exportCardFilter = cards;
    }

    public void clearExportCardFilter() {
        setExportCardFilter(null);
    }

    public List<PersonalCard> getCardsForExport(PersonalCardStore store) {
        if (exportCardFilter == null) {
            return store.getPersonalCards();
        } else {
            List<PersonalCard> out = new ArrayList<>(store.getPersonalCards());
            out.retainAll(exportCardFilter);
            return out;
        }
    }
}
