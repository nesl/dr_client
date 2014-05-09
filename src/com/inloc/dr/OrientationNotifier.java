package com.inloc.dr;

import java.util.ArrayList;

import com.inloc.dr.StepDisplayer.Listener;

public class OrientationNotifier implements OrientationListener{
	private int currentOrientation;
	
	public interface Listener {
        public void onChange(int value);
        public void passValue();
    }
	
	private ArrayList<Listener> mListeners = new ArrayList<Listener>();

    public void addListener(Listener l) {
        mListeners.add(l);
    }
    
    public void notifyListener() {
        for (Listener listener : mListeners) {
            listener.onChange(currentOrientation);
        }
    }

	@Override
	public void onChange(int newOrient) {
		currentOrientation = newOrient;
		notifyListener();
	}

	@Override
	public void passValue() {}
	
}
