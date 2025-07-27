package cz.nocard.android.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import cz.nocard.android.NoCardApplication;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;

public class ExportActivityCommon {

    public static final String EXTRA_CARD_IDS = "card_ids";

    @Inject
    PersonalCardStore personalCardStore;

    private final Activity activity;

    private int[] cardIDs;
    private Set<Integer> cardIDSet;

    public ExportActivityCommon(Activity activity) {
        this.activity = activity;
        NoCardApplication.getInstance().getApplicationComponent().inject(this);

        Intent intent = activity.getIntent();
        if (intent != null) {
            cardIDs = intent.getIntArrayExtra(EXTRA_CARD_IDS);
            if (cardIDs != null) {
                cardIDSet = new LinkedHashSet<>();
                for (int cardID : cardIDs) {
                    cardIDSet.add(cardID);
                }
            }
        }
    }

    public static Intent newIntent(Context context, Class<? extends Activity> clazz, int[] cardIDs) {
        return new Intent(context, clazz).putExtra(EXTRA_CARD_IDS, cardIDs);
    }

    public List<PersonalCard> getCardsToExport() {
        if (cardIDs == null) {
            return personalCardStore.getPersonalCards();
        } else {
            return personalCardStore.getPersonalCards()
                    .stream()
                    .filter(personalCard -> cardIDSet.contains(personalCard.id()))
                    .toList();
        }
    }

    public Intent forward(Class<? extends Activity> toWhom) {
        return newIntent(activity, toWhom, cardIDs);
    }
}
