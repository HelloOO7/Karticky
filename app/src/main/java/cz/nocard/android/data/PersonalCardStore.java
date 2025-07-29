package cz.nocard.android.data;

import android.content.SharedPreferences;
import android.os.Looper;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import cz.nocard.android.util.AbstractListenerTarget;

public class PersonalCardStore extends AbstractListenerTarget<PersonalCardStore.Listener> {

    public static final int CARD_ID_INVALID = -1;
    public static final int CARD_ID_TEMPORARY = -2;

    private static final String PREF_KEY = "personal_cards";
    private static final String ID_AUTOINCREMENT_PREF_KEY = "personal_card_id_autoincrement";

    private final SharedPreferences sharedPreferences;
    private List<PersonalCard> personalCards;
    private int inTransaction = 0;
    private boolean dirty = false;
    private Set<Listener> mutedListeners = new HashSet<>();

    public PersonalCardStore(NoCardPreferences preferences) {
        sharedPreferences = preferences.getPrefs();
    }

    public synchronized int newCardId() {
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

    public synchronized List<PersonalCard> getPersonalCards() {
        if (personalCards == null) {
            personalCards = SharedPrefsHelper.loadObject(sharedPreferences, PREF_KEY, SettingValueType.class);
        }
        if (personalCards == null) {
            personalCards = new SettingValueType();
        }
        return personalCards;
    }

    private synchronized void addCard(int index, PersonalCard card) {
        if (card.id() == CARD_ID_TEMPORARY) {
            card = new PersonalCard(newCardId(), card.name(), card.provider(), card.customProperties(), card.cardNumber());
        }
        final PersonalCard card_ = card;
        getPersonalCards().add(index, card);
        persistAndFinish();
        invokeListeners(listener -> listener.onCardAdded(card_));
    }

    private void persistAndFinish() {
        persist();
        finishOperation();
    }

    private void finishOperation() {
        if (inTransaction == 0) {
            mutedListeners.clear();
        }
    }

    public synchronized void addCard(PersonalCard card) {
        addCard(getPersonalCards().size(), card);
    }

    public synchronized void merge(List<PersonalCard> personalCards) {
        try {
            beginTransaction();
            for (PersonalCard card : personalCards) {
                merge(card);
            }
        } finally {
            endTransaction();
        }
    }

    public boolean cardAlreadyExists(PersonalCard card) {
        return findSameCard(card) != null;
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

    public synchronized void merge(PersonalCard card) {
        PersonalCard existing = findSameCard(card);
        if (existing != null) {
            return;
        } else {
            addCard(card);
        }

        persistAndFinish();
    }

    @Override
    protected boolean canInvokeListener(Listener listener) {
        return !mutedListeners.contains(listener);
    }

    public synchronized void removeCard(PersonalCard card) {
        getPersonalCards().remove(card);
        persistAndFinish();
        invokeListeners(listener -> listener.onCardRemoved(card));
    }

    private void invokeCardChanged(PersonalCard card) {
        invokeListeners(listener -> listener.onCardChanged(card));
    }

    public String getCardProviderName(PersonalCard card, ConfigManager config) {
        PersonalCard.CustomCardProperties props = card.customProperties();
        if (props != null) {
            return props.providerName();
        } else {
            return config.getProviderNameOrDefault(card.provider());
        }
    }

    public String getCardName(PersonalCard card, ConfigManager config) {
        if (card.name() == null) {
            return PersonalCard.formatDefaultName(getCardProviderName(card, config), card.cardNumber());
        } else {
            return card.name();
        }
    }

    public String getCardSingleLineName(PersonalCard card, ConfigManager configManager) {
        return getCardName(card, configManager).replace("\n", " ");
    }

    public NoCardConfig.ProviderInfo getCardProviderInfo(PersonalCard card, ConfigManager configManager) {
        PersonalCard.CustomCardProperties custom = card.customProperties();
        if (custom != null) {
            return new NoCardConfig.ProviderInfo(
                    custom.providerName(),
                    custom.providerName(),
                    custom.color(),
                    custom.color(),
                    custom.format(),
                    List.of(card.cardNumber())
            );
        }
        return configManager.getProviderInfoOrNull(card.provider());
    }

    public synchronized void renameCard(PersonalCard card, String newName) {
        card.rename(newName);
        persistAndFinish();
        invokeCardChanged(card);
    }

    public int getCardOrdinal(PersonalCard card) {
        return getPersonalCards().indexOf(card);
    }

    /**
     * Move a card after another card. The card will effectively be removed from its
     * current position and reinserted after the specified card.
     *
     * @param card The card to move
     * @param after The card after which to move the specified card. If null or not found,
     *              the card will be moved to the start of the list. If this is the same as
     *              "card", then the card will be swapped with its successor.
     */
    public synchronized void reorderCardAfter(PersonalCard card, PersonalCard after) {
        beginTransaction();
        int insertIndex = getPersonalCards().indexOf(after);
        removeCard(card);
        if (insertIndex == -1) {
            insertIndex = 0;
        } else {
            insertIndex++;
        }
        addCard(insertIndex, card);
        endTransaction();
    }

    public synchronized void persist() {
        if (inTransaction > 0) {
            dirty = true;
            return; // Don't persist during a transaction
        }
        if (personalCards == null) {
            //not loaded
            return;
        }
        SharedPrefsHelper.saveObject(sharedPreferences, PREF_KEY, personalCards, Looper.getMainLooper().isCurrentThread());
    }

    public void muteListenerForNextOperation(Listener listener) {
        mutedListeners.add(listener);
    }

    private void beginTransaction() {
        inTransaction++;
    }

    private void endTransaction() {
        if (inTransaction > 0) {
            inTransaction--;
        }
        if (inTransaction == 0) {
            if (dirty) {
                persist();
                dirty = false;
            }
        }
        finishOperation();
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
