package com.net168.audiorecorddemo;

import android.app.Application;
import android.os.Handler;
import android.widget.Toast;
import com.net168.bt.ScoController;

public class App extends Application {

    private ScoController scoController;

    @Override
    public void onCreate() {
        super.onCreate();
        scoController = new ScoController(this.getApplicationContext(), null);
        new Handler().post(new Runnable() {
            @Override
            public void run() {


                boolean ret = scoController.start();
                if (!ret) {
                    Toast.makeText(App.this, "请先连接智能AI蓝牙耳机", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    public ScoController getScoController() {
        return scoController;
    }
}
