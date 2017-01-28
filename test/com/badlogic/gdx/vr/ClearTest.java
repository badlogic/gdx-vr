package com.badlogic.gdx.vr;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.vr.VRContext.Eye;

public class ClearTest extends ApplicationAdapter {
	
	VRContext context;
	
	@Override
	public void create () {
		context = new VRContext();
	}

	@Override
	public void render () {
		int glError = Gdx.gl.glGetError();
		context.pollEvents();
		context.begin();
		context.beginEye(Eye.Left);
		Gdx.gl.glClearColor(1, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		context.endEye();
		context.beginEye(Eye.Right);
		Gdx.gl.glClearColor(0, 1, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		context.endEye();
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
