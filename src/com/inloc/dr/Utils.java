package com.inloc.dr;

import java.util.Locale;

import android.app.Service;
import android.speech.tts.TextToSpeech;
import android.text.format.Time;
import android.util.Log;

public class Utils  {
    private static final String TAG = "Utils";
    private Service mService;

    private static Utils instance = null;

    private Utils() {
    }
     
    public static Utils getInstance() {
        if (instance == null) {
            instance = new Utils();
        }
        return instance;
    }
    
    public void setService(Service service) {
        mService = service;
    }
    
    
    /********** Time **********/
    
    public static long currentTimeInMillis() {
        Time time = new Time();
        time.setToNow();
        return time.toMillis(false);
    }
}
