package com.example.curtiss;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class InstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_COMPLETE = "com.example.curtiss.INSTALL_COMPLETE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_INSTALL_COMPLETE.equals(intent.getAction())) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // System requires the user to explicitly confirm the install
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
            } else if (status == PackageInstaller.STATUS_SUCCESS) {
                // Install was fully silent and successful!
                restartApp(context);
            }
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            // App was just updated and system woke us up, restart it!
            restartApp(context);
        }
    }

    private void restartApp(Context context) {
        Intent launchIntent = new Intent(context, SplashActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(launchIntent);
    }
}
