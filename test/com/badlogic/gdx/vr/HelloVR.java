package com.badlogic.gdx.vr;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.vr.VRContext.Eye;
import com.badlogic.gdx.vr.VRContext.VRControllerButtons;
import com.badlogic.gdx.vr.VRContext.VRControllerRole;
import com.badlogic.gdx.vr.VRContext.VRDevice;
import com.badlogic.gdx.vr.VRContext.VRDeviceListener;
import com.badlogic.gdx.vr.VRContext.VRDeviceType;

public class HelloVR extends ApplicationAdapter {
	VRContext context;
	ModelBatch batch;
	Environment environment;
	Model cubeModel;
	Array<ModelInstance> cubes = new Array<ModelInstance>();

	@Override
	public void create() {
		context = new VRContext(false);
		context.resizeCompanionWindow();
		createScene();
		
		context.addListener(new VRDeviceListener() {						
			@Override
			public void connected(VRDevice device) {
				System.out.println(device + " connected");
			}
			
			@Override
			public void disconnected(VRDevice device) {
				System.out.println(device + " disconnected");
			}			
			
			@Override
			public void buttonPressed(VRDevice device, int button) {
				System.out.println(device + " button pressed: " + button);
				device.triggerHapticPulse((short)1000);
			}
			
			@Override
			public void buttonReleased(VRDevice device, int button) {
				System.out.println(device + " button released: " + button);				
			}
		});
	}

	private void createScene() {
		batch = new ModelBatch();
		ModelBuilder modelBuilder = new ModelBuilder();
		cubeModel = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.GREEN)),
				Usage.Position | Usage.Normal);

		for (int z = -3; z <= 3; z += 3) {
			for (int y = -3; y <= 3; y += 3) {
				for (int x = -3; x <= 3; x += 3) {
					if (x == 0 && y == 0 && z == 0)
						continue;
					ModelInstance cube = new ModelInstance(cubeModel);
					cube.transform.translate(x, y, z);
					cubes.add(cube);
				}
			}
		}

		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
		environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
	}

	@Override
	public void render() {
		// render the scene for the left/right eye
		context.begin();
		renderScene(Eye.Left);
		renderScene(Eye.Right);
		context.end();

		// render the left eye result to the companion window
		Gdx.gl.glClearColor(1, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		context.renderToCompanionWindow(Eye.Left);
		
		VRDevice device = context.getDeviceByType(VRDeviceType.Controller);
	}

	private void renderScene(Eye eye) {
		VRCamera camera = context.getEyeData(eye).cameras;

		context.beginEye(eye);

		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		batch.begin(camera);
		for (ModelInstance cube : cubes)
			batch.render(cube, environment);
		batch.end();

		context.endEye();
	}

	@Override
	public void dispose() {
		context.dispose();
		batch.dispose();
		cubeModel.dispose();
	}

	public static void main(String[] args) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.useVsync(false);
		new Lwjgl3Application(new HelloVR(), config);
	}
}