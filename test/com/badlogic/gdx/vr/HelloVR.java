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
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.vr.VRContext.Eye;
import com.badlogic.gdx.vr.VRContext.Space;
import com.badlogic.gdx.vr.VRContext.VRControllerButtons;
import com.badlogic.gdx.vr.VRContext.VRDevice;
import com.badlogic.gdx.vr.VRContext.VRDeviceListener;
import com.badlogic.gdx.vr.VRContext.VRDeviceType;

public class HelloVR extends ApplicationAdapter {
	VRContext context;
	ModelBatch batch;
	Environment environment;
	Model cubeModel;
	Array<ModelInstance> modelInstances = new Array<ModelInstance>();	
	ShapeRenderer renderer;
	Model discModel;
	ModelInstance disc;
	boolean isTeleporting = false;

	@Override
	public void create() {
		context = new VRContext(2.0f, false);
		context.resizeCompanionWindow();
		createScene();
		
		context.addListener(new VRDeviceListener() {						
			@Override
			public void connected(VRDevice device) {
				System.out.println(device + " connected");
				if (device.getType() == VRDeviceType.Controller && device.getModelInstance() != null) modelInstances.add(device.getModelInstance());
			}
			
			@Override
			public void disconnected(VRDevice device) {
				System.out.println(device + " disconnected");
				if (device.getType() == VRDeviceType.Controller && device.getModelInstance() != null) modelInstances.removeValue(device.getModelInstance(), true);
			}			
			
			@Override
			public void buttonPressed(VRDevice device, int button) {
				System.out.println(device + " button pressed: " + button);				
				if (device == context.getDeviceByType(VRDeviceType.Controller)) {
					if (button == VRControllerButtons.SteamVR_Trigger) isTeleporting = true;
				}
			}
			
			@Override
			public void buttonReleased(VRDevice device, int button) {
				System.out.println(device + " button released: " + button);
				if (device == context.getDeviceByType(VRDeviceType.Controller)) {
					if (button == VRControllerButtons.SteamVR_Trigger) {
						if (intersectControllerXZPlane(context.getDeviceByType(VRDeviceType.Controller), tmp)) {
							// Teleportation
							// - Tracker space origin in world space is initially at [0,0,0]
							// - When teleporting, we want to set the tracker space origin in world space to the
							//   teleportation point
							// - Then we need to offset the tracker space origin in world space by the camera
							//   x/z position so the camera is at the teleportation point in world space							
							tmp2.set(context.getDeviceByType(VRDeviceType.HeadMountedDisplay).getPosition(Space.Tracker));
							tmp2.y = 0;
							tmp.sub(tmp2);
							
							context.getTrackerSpaceToWorldSpace().idt().translate(tmp);
						}
						isTeleporting = false;
					}
				}
			}
		});
	}
	
	Plane xzPlane = new Plane(Vector3.Y, 0);
	Ray ray = new Ray();
	Vector3 tmp = new Vector3();
	Vector3 tmp2 = new Vector3();
	
	private boolean intersectControllerXZPlane(VRDevice controller, Vector3 intersection) {
		ray.origin.set(controller.getPosition(Space.World));
		ray.direction.set(controller.getDirection(Space.World).nor());		
		return Intersector.intersectRayPlane(ray, xzPlane, intersection);
	}	

	private void createScene() {
		batch = new ModelBatch();
		renderer = new ShapeRenderer();
		
		ModelBuilder modelBuilder = new ModelBuilder();
		
		discModel = modelBuilder.createCylinder(1, 0.1f, 1, 20, new Material(ColorAttribute.createDiffuse(Color.CORAL)), Usage.Position | Usage.Normal);
		disc = new ModelInstance(discModel);
		
		cubeModel = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.GREEN)),
				Usage.Position | Usage.Normal);
		
		Color[] colors = new Color[] {
				Color.RED,
				Color.GREEN, 
				Color.BLUE,
				Color.YELLOW,
				Color.MAGENTA,
				Color.GOLD,
				Color.ORANGE
		};
		
		int idx = 0;
		for (int z = -3; z <= 3; z += 3) {
			for (float y = -0.5f; y <= 9 - 0.5f; y += 3) {
				for (int x = -3; x <= 3; x += 3) {
					if (x == 0 && y == 0 && z == 0)
						continue;
					ModelInstance cube = new ModelInstance(cubeModel);
					cube.materials.get(0).get(ColorAttribute.class, ColorAttribute.Diffuse).color.set(colors[idx++]);
					if (idx > colors.length - 1) idx = 0;
					cube.transform.translate(x, y, z);
					modelInstances.add(cube);
				}
			}
		}
		
		Model apartmentModel = new G3dModelLoader(new JsonReader()).loadModel(Gdx.files.classpath("apartment.g3dj"));
		ModelInstance apartment = new ModelInstance(apartmentModel);
		apartment.transform.scale(1 / 100f, 1 / 100f, 1 / 100f).setTranslation(-10, 0, 0);
		modelInstances.add(apartment);

		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
		environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
	}

	@Override
	public void render() {
		// poll the latest tracking data. must be called
		// before context.begin()!
		context.pollEvents();
		
		// check if we are about to teleport
		modelInstances.removeValue(disc, true);
		if (isTeleporting) {
			if (intersectControllerXZPlane(context.getDeviceByType(VRDeviceType.Controller), tmp)) {
				disc.transform.idt().translate(tmp);
				modelInstances.add(disc);
			}
		}
		
		// render the scene for the left/right eye
		context.begin();
		renderScene(Eye.Left);
		renderScene(Eye.Right);
		context.end();

		// render the left eye result to the companion window
		Gdx.gl.glClearColor(1, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		context.renderToCompanionWindow(Eye.Left);			
	}
	
	Vector3 position = new Vector3();
	Vector3 xAxis = new Vector3();
	Vector3 yAxis = new Vector3();
	Vector3 zAxis = new Vector3();
	
	private void renderScene(Eye eye) {
		VRCamera camera = context.getEyeData(eye).camera;

		context.beginEye(eye);

		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

		// render all the models in the scene
		batch.begin(camera);
		for (ModelInstance modelInstance : modelInstances)
			batch.render(modelInstance, environment);
		batch.end();
		
		// render coordinate system axes for orientation
		renderer.setProjectionMatrix(camera.combined);
		renderer.begin(ShapeType.Line);
//		renderer.setColor(Color.WHITE);
//		renderer.line(-100, 0, 0, 0, 0, 0);
//		renderer.line(0, -100, 0, 0, 0, 0);
//		renderer.line(0, 0, -100, 0, 0, 0);
//		renderer.setColor(Color.RED);
//		renderer.line(0, 0, 0, 100, 0, 0);
//		renderer.setColor(Color.GREEN);
//		renderer.line(0, 0, 0, 0, 100, 0);
//		renderer.setColor(Color.BLUE);
//		renderer.line(0, 0, 0, 0, 0, 100);
		
		
		/** render direction, up and right axes of each controller **/
		for (VRDevice device: context.getDevices()) {
			if (device.getType() == VRDeviceType.Controller) {
				renderer.setColor(Color.BLUE);
				Vector3 pos = tmp.set(device.getPosition(Space.World));
				Vector3 dir = tmp2.set(device.getDirection(Space.World)).scl(0.5f);
				renderer.line(device.getPosition(Space.World), pos.add(dir));
				
				renderer.setColor(Color.GREEN);
				pos = tmp.set(device.getPosition(Space.World));
				dir = tmp2.set(device.getUp(Space.World)).scl(0.1f);
				renderer.line(device.getPosition(Space.World), pos.add(dir));
				
				renderer.setColor(Color.RED);
				pos = tmp.set(device.getPosition(Space.World));
				dir = tmp2.set(device.getRight(Space.World)).scl(0.1f);
				renderer.line(device.getPosition(Space.World), pos.add(dir));
			}
		}
		renderer.end();

		context.endEye();
	}

	@Override
	public void dispose() {
		context.dispose();
		batch.dispose();
		cubeModel.dispose();
		discModel.dispose();
		renderer.dispose();
	}

	public static void main(String[] args) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.useVsync(false);
		new Lwjgl3Application(new HelloVR(), config);
	}
}