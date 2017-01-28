package com.badlogic.gdx.vr;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class ClearTest extends ApplicationAdapter {
	
	VRContext context;
	
	@Override
	public void create () {
		context = new VRContext();
	}

	@Override
	public void render () {
		context.pollEvents();
		context.begin();
		context.end();
	}

	@Override
	public void dispose () {
		context.dispose();
	}

	public static void main (String[] args) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.useVsync(false);
		new Lwjgl3Application(new ClearTest(), config);
	}
}
