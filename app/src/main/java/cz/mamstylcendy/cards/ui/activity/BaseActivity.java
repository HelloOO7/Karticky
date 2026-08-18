package cz.mamstylcendy.cards.ui.activity;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import cz.spojenka.android.ui.helpers.EdgeToEdgeSupport;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        EdgeToEdgeSupport.registerCompatInsetsFixups(this);
    }
}
