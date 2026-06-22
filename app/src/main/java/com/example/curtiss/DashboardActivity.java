package com.example.curtiss;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
            bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (itemId == R.id.nav_customers) {
                        Intent intent = new Intent(DashboardActivity.this, CustomerActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (itemId == R.id.nav_history) {
                        Intent intent = new Intent(DashboardActivity.this, HistoryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (itemId == R.id.nav_dashboard) {
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
        }
    }
}
