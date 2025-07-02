package cz.nocard.android;

import android.content.SharedPreferences;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class PersonalCardStore {

    public static final int CARD_ID_INVALID = -1;
    public static final int CARD_ID_TEMPORARY = -2;

    private static final String PREF_KEY = "personal_cards";
    private static final String ID_AUTOINCREMENT_PREF_KEY = "personal_card_id_autoincrement";

    private final SharedPreferences sharedPreferences;
    private List<PersonalCard> personalCards;
    private List<Listener> listeners = new ArrayList<>();
    private int inTransaction = 0;
    private boolean dirty = false;

    public PersonalCardStore(NoCardPreferences preferences) {
        sharedPreferences = preferences.getPrefs();
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public int newCardId() {
        int id = sharedPreferences.getInt(ID_AUTOINCREMENT_PREF_KEY, 1);
        sharedPreferences.edit().putInt(ID_AUTOINCREMENT_PREF_KEY, id + 1).apply();
        return id;
    }

    public PersonalCard getCardById(int id) {
        return getPersonalCards().stream()
                .filter(card -> card.id() == id)
                .findFirst()
                .orElse(null);
    }

    public PersonalCard getCardForProvider(String providerId) {
        return getPersonalCards().stream()
                .filter(card -> card.provider().equals(providerId))
                .findFirst()
                .orElse(null);
    }

    public List<PersonalCard> getPersonalCards() {
        if (personalCards == null) {
            personalCards = SharedPrefsHelper.loadObject(sharedPreferences, PREF_KEY, SettingValueType.class);
        }
        if (personalCards == null) {
            personalCards = new SettingValueType();
        }
        return personalCards;
    }

    private void invokeListeners(Consumer<Listener> action) {
        for (Listener listener : listeners) {
            action.accept(listener);
        }
    }

    public void addCard(PersonalCard card) {
        if (card.id() == CARD_ID_TEMPORARY) {
            card = new PersonalCard(newCardId(), card.name(), card.provider(), card.customProperties(), card.cardNumber());
        }
        final PersonalCard card_ = card;
        getPersonalCards().add(card);
        persist();
        invokeListeners(listener -> listener.onCardAdded(card_));
    }

    public void merge(List<PersonalCard> personalCards) {
        try {
            beginTransaction();
            for (PersonalCard card : personalCards) {
                merge(card);
            }
        } finally {
            endTransaction();
        }
    }

    private PersonalCard findSameCard(PersonalCard card) {
        for (PersonalCard other : getPersonalCards()) {
            if (other.cardNumber().equals(card.cardNumber())) {
                if (Objects.equals(other.provider(), card.provider())) {
                    if (!other.isCustom()) {
                        return other;
                    } else {
                        PersonalCard.CustomCardProperties srcCustomProps = card.customProperties();
                        PersonalCard.CustomCardProperties dstCustomProps = other.customProperties();
                        if (srcCustomProps != null && dstCustomProps != null) {
                            if (srcCustomProps.providerName().equals(dstCustomProps.providerName()) && srcCustomProps.format() == dstCustomProps.format()) {
                                return other;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public void merge(PersonalCard card) {
        PersonalCard existing = findSameCard(card);
        if (existing != null) {
            return;
        } else {
            addCard(card);
        }

        persist();
    }

    public void removeCard(PersonalCard card) {
        getPersonalCards().remove(card);
        persist();
        invokeListeners(listener -> listener.onCardRemoved(card));
    }

    private void invokeCardChanged(PersonalCard card) {
        invokeListeners(listener -> listener.onCardChanged(card));
    }

    public void renameCard(PersonalCard card, String newName) {
        card.rename(newName);
        persist();
        invokeCardChanged(card);
    }

    public void persist() {
        if (inTransaction > 0) {
            dirty = true;
            return; // Don't persist during a transaction
        }
        SharedPrefsHelper.saveObject(sharedPreferences, PREF_KEY, personalCards, true);
    }

    public void beginTransaction() {
        inTransaction++;
    }

    public void endTransaction() {
        if (inTransaction > 0) {
            inTransaction--;
        }
        if (inTransaction == 0 && dirty) {
            persist();
            dirty = false;
        }
    }

    @Keep
    private static class SettingValueType extends ArrayList<PersonalCard> {

    }

    public static interface Listener {

        public void onCardAdded(PersonalCard card);
        public void onCardRemoved(PersonalCard card);
        public void onCardChanged(PersonalCard card);
    }
}
