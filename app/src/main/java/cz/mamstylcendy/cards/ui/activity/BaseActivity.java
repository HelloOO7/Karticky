package cz.mamstylcendy.cards.ui.activity;

import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import cz.spojenka.android.ui.helpers.EdgeToEdgeSupport;

public class BaseActivity extends AppCompatActivity {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        EdgeToEdge.enable(this);
        EdgeToEdgeSupport.registerCompatInsetsFixups(this);
    }
}
