package com.kist.externalwebbrowser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class MyBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w("BroadcastReceiver", "onReceive");

        String value = intent.getExtras().getString("event");
        Log.w("BroadcastReceiver", "onReceive");
        Log.w("BroadcastReceiver", "onReceive - intent is: "+value);
        if(value.equals("stop")){
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
