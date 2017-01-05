package com.badlogic.gdx.vr;

import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import vr.HmdMatrix34_t;
import vr.HmdMatrix44_t;
import vr.IVRCompositor_FnTable;
import vr.IVRSystem;
import vr.Texture_t;
import vr.TrackedDevicePose_t;
import vr.VR;

/**
 * Responsible for initializing the VR system, managing rendering surfaces,
 * getting tracking device poses, submitting the rendering results to the HMD
 * and rendering the surfaces side by side to the companion window on the
 * desktop. Wrapper around OpenVR.
 * 
 * 	FIXME add multisampling plus draw/resolve buffers
 */
public class VRContext implements Disposable {
	/** device index of the head mounted display **/
	public static final int HMD_DEVICE_INDEX = VR.k_unTrackedDeviceIndex_Hmd;
	
	/** maximum device index **/
	public static final int MAX_DEVICE_INDEX = VR.k_unMaxTrackedDeviceCount - 1;
	
	/**
	 * Used to select for which eye a specific property should be accessed.
	 */
	public static enum Eye {
		Left(0), Right(1);

		final int index;

		Eye(int index) {
			this.index = index;
		}
	}	

	// couple of scratch buffers
	private final IntBuffer error = BufferUtils.newIntBuffer(1);
	private final IntBuffer scratch = BufferUtils.newIntBuffer(1), scratch2 = BufferUtils.newIntBuffer(1);
	
	// OpenVR components 
	final IVRSystem system;
	final IVRCompositor_FnTable compositor;
	
	// per eye data such as rendering surfaces, textures, regions, cameras etc. for each eye
	private final VRPerEyeData perEyeData[] = new VRPerEyeData[2];
	
	// batcher to draw eye rendering surface to companion window
	private final SpriteBatch batcher;
	
	// internal native objects to get device poses 
	private final TrackedDevicePose_t.ByReference trackedDevicePosesReference = new TrackedDevicePose_t.ByReference();
    private final TrackedDevicePose_t[] trackedDevicePoses = (TrackedDevicePose_t[]) trackedDevicePosesReference.toArray(VR.k_unMaxTrackedDeviceCount);
    private final VRDevicePose[] devicePoses = new VRDevicePose[trackedDevicePoses.length];
    
    // book keeping
	private Eye currentEye = null;
	private boolean renderingStarted = false;

	/**
	 * Creates a new VRContext, initializes the VR system,
	 * and sets up rendering surfaces.
	 * 
	 * @param hasStencil whether the rendering surfaces should have a stencil buffer
	 * @throws {@link GdxRuntimeException} if the system could not be initialized 
	 */
	public VRContext(boolean hasStencil) {
		system = VR.VR_Init(error, VR.EVRApplicationType.VRApplication_Scene);
		checkInitError(error);
		
		compositor = new IVRCompositor_FnTable(VR.VR_GetGenericInterface(VR.IVRCompositor_Version, error));
		checkInitError(error);
		
		for (int i = 0; i < devicePoses.length; i++) {
			devicePoses[i] = new VRDevicePose(i);
		}
		
		system.GetRecommendedRenderTargetSize.apply(scratch, scratch2);
		int width = scratch.get(0);
		int height = scratch2.get(0);
		
		setupEye(Eye.Left, width, height, hasStencil);
		setupEye(Eye.Right, width, height, hasStencil);
		
		batcher = new SpriteBatch();
	}
	
	private void setupEye(Eye eye, int width, int height, boolean hasStencil) {
		FrameBuffer buffer = new FrameBuffer(Format.RGBA8888, width, height, true, hasStencil);		
		TextureRegion region = new TextureRegion(buffer.getColorBufferTexture());
		region.flip(false, true);
		VRCamera camera = new VRCamera(this, eye);
		camera.near = 0.1f;
		camera.far = 1000f;
		perEyeData[eye.index] = new VRPerEyeData(buffer, region, camera);
	}
	
	private void checkInitError(IntBuffer errorBuffer) {
		if (errorBuffer.get(0) != VR.EVRInitError.VRInitError_None) {
			int error = errorBuffer.get(0);
			throw new GdxRuntimeException("VR Initialization error: " + VR.VR_GetVRInitErrorAsEnglishDescription(error).getString(0));
		}
	}
	
	/**
	 * Start rendering. Call beginEye to setup rendering
	 * for each individual eye. End rendering by calling
	 * #end
	 */
	public void begin() {
		if (renderingStarted) throw new GdxRuntimeException("Last begin() call not completed, call end() before starting a new render");
		renderingStarted = true;
		
		compositor.WaitGetPoses.apply(trackedDevicePosesReference, VR.k_unMaxTrackedDeviceCount, null, 0);
		updateDevicePoses();
		perEyeData[Eye.Left.index].cameras.update();
		perEyeData[Eye.Right.index].cameras.update();
	}
	
	private void updateDevicePoses() {        
        for (int device = 0; device < VR.k_unMaxTrackedDeviceCount; device++) {
			TrackedDevicePose_t trackedPose = trackedDevicePoses[device];
			VRDevicePose pose = devicePoses[device];
			
			hmdMat34ToMatrix4(trackedPose.mDeviceToAbsoluteTracking, pose.transform);
			pose.velocity.set(trackedPose.vVelocity.v);
			pose.angularVelocity.set(trackedPose.vAngularVelocity.v);
			pose.isConnected = trackedPose.bDeviceIsConnected != 0;
			pose.isValid = trackedPose.bPoseIsValid != 0;
        }
    }

	/**
	 * @param deviceIndex device index, {@link #HMD_DEVICE_INDEX} for the head mounted display
	 * @return the {@link VRDevicePose}
	 */
	public VRDevicePose getDevicePose(int deviceIndex) {
		if (deviceIndex < 0 || deviceIndex >= devicePoses.length) throw new IndexOutOfBoundsException("Device index must be >= 0 and <= " + devicePoses.length);
		return devicePoses[deviceIndex];
	}
	
	/**
	 * @return the {@link VRPerEyeData} such as rendering surface and camera
	 */
	public VRPerEyeData getEyeData(Eye eye) {
		return perEyeData[eye.index];
	}
	
	/**
	 * Start rendering to the rendering surface for the given eye.
	 * Complete by calling {@link #endEye()}.
	 */
	public void beginEye(Eye eye) {
		if (!renderingStarted) throw new GdxRuntimeException("Call begin() before calling beginEye()");
		if (currentEye != null) throw new GdxRuntimeException("Last beginEye() call not completed, call endEye() before starting a new render");
		currentEye = eye;
		perEyeData[eye.index].buffer.begin();
	}
	
	/**
	 * Completes rendering to the rendering surface for the given eye.
	 */
	public void endEye() {
		if (currentEye == null) throw new GdxRuntimeException("Call beginEye() before endEye()");
		perEyeData[currentEye.index].buffer.end();
		currentEye = null;		
	}
	
	/**
	 * Completes rendering and submits the rendering surfaces to the
	 * head mounted display.
	 */
	public void end() {
		if (!renderingStarted) throw new GdxRuntimeException("Call begin() before end()");
		renderingStarted = false;
		
		compositor.Submit.apply(VR.EVREye.EYE_Left, perEyeData[Eye.Left.index].texture, null, VR.EVRSubmitFlags.Submit_Default);
		compositor.Submit.apply(VR.EVREye.Eye_Right, perEyeData[Eye.Right.index].texture, null, VR.EVRSubmitFlags.Submit_Default);
		Gdx.gl.glFinish();
		compositor.PostPresentHandoff.apply();		
	}
	
	public void dispose() {
		for (VRPerEyeData eyeData: perEyeData)
			eyeData.buffer.dispose();
		batcher.dispose();
		VR.VR_Shutdown();
	}
	
	/**
	 * Resizes the companion window so the rendering buffers
	 * can be displayed without stretching.
	 */
	public void resizeCompanionWindow() {
		FrameBuffer buffer = perEyeData[0].buffer;
		Gdx.graphics.setWindowedMode(buffer.getWidth(), buffer.getHeight());
	}
	
	/**
	 * Renders the content of the given eye's rendering surface
	 * to the entirety of the companion window.
	 */
	public void renderToCompanionWindow(Eye eye) {		
		FrameBuffer buffer = perEyeData[eye.index].buffer;
		TextureRegion region = perEyeData[eye.index].region;		
		batcher.getProjectionMatrix().setToOrtho2D(0, 0, buffer.getWidth(), buffer.getHeight());
		batcher.begin();
		batcher.draw(region, 0, 0);
		batcher.end();
	}
	
	/**
	 * Keeps track of per eye data such as rendering surface,
	 * or {@link VRCamera}.
	 */
	public static class VRPerEyeData {
		/** the {@link FrameBuffer} for this eye */
		public final FrameBuffer buffer;		
		/** a {@link TextureRegion} wrapping the color texture of the framebuffer for 2D rendering **/
		public final TextureRegion region;
		/** the {@link VRCamera} for this eye **/
		public final VRCamera cameras;		
		/** used internally to submit the frame buffer to OpenVR **/
		final Texture_t texture;
		
		VRPerEyeData(FrameBuffer buffer, TextureRegion region, VRCamera cameras) {
			this.buffer = buffer;
			this.region = region;
			this.cameras = cameras;
			this.texture = new Texture_t(buffer.getColorBufferTexture().getTextureObjectHandle(), VR.EGraphicsAPIConvention.API_OpenGL, VR.EColorSpace.ColorSpace_Gamma);
		}
	}
	
	/**
	 * Represents the pose of a {@link VRDevice}, including its
	 * transform, velocity and angular velocity. Also indicates
	 * whether the pose is valid and whether the device is connected. 
	 */
	static class VRDevicePose {
		/** the world space transformation **/
		public final Matrix4 transform = new Matrix4();
		/** the velocity in m/s in world space **/
		public final Vector3 velocity = new Vector3();
		/** the angular velocity in radians/s **/
		public final Vector3 angularVelocity = new Vector3();
		/** whether the pose is valid our invalid, e.g. outdated because of tracking failure**/
		public boolean isValid;
		/** whether the device is connected **/
		public boolean isConnected;
		/** the device index **/
		private final int index;
		
		public VRDevicePose(int index) {
			this.index = index;
		}
	}
	
	/**
	 * Represents a tracked VR device such as the head mounted
	 * display, wands etc.
	 */
	public class VRDevice {		
		private final VRDevicePose pose;		
		
		VRDevice(VRDevicePose pose) {
			this.pose = pose;
		}
		
		/**
		 * @return the most up-to-date {@link VRDevicePose}
		 */
		public VRDevicePose getPose() {
			return pose;
		}			
		
		void update() {			
		}
	}
	
	static void hmdMat4toMatrix4(HmdMatrix44_t hdm, Matrix4 mat) {
		float[] val = mat.val;
		float[] m = hdm.m;
		
		val[0] = m[0];
		val[1] = m[4];
		val[2] = m[8];
		val[3] = m[12];
		
		val[4] = m[1];
		val[5] = m[5];
		val[6] = m[9];
		val[7] = m[13];
		
		val[8] = m[2];
		val[9] = m[6];
		val[10] = m[10];
		val[11] = m[14];
		
		val[12] = m[3];
		val[13] = m[7];
		val[14] = m[11];
		val[15] = m[15];
	}
	
	static void hmdMat34ToMatrix4(HmdMatrix34_t hmd, Matrix4 mat) {
		float[] val = mat.val;
		float[] m = hmd.m;
		
		val[0] = m[0];
		val[1] = m[4];
		val[2] = m[8];
		val[3] = 0;
		
		val[4] = m[1];
		val[5] = m[5];
		val[6] = m[9];
		val[7] = 0;
		
		val[8] = m[2];
		val[9] = m[6];
		val[10] = m[10];
		val[11] = 0;
		
		val[12] = m[3];
		val[13] = m[7];
		val[14] = m[11];
		val[15] = 1;
	}
}