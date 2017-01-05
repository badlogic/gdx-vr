package com.badlogic.gdx.vr;

import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

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

	private final IntBuffer error = BufferUtils.newIntBuffer(1);
	private final IntBuffer scratch = BufferUtils.newIntBuffer(1), scratch2 = BufferUtils.newIntBuffer(1);
	final IVRSystem system;
	final IVRCompositor_FnTable compositor;
	private final FrameBuffer[] buffers = new FrameBuffer[Eye.values().length];
	private final Texture_t[] textures = new Texture_t[Eye.values().length];
	private final TextureRegion[] regions = new TextureRegion[Eye.values().length];
	private final VRCamera[] cameras = new VRCamera[Eye.values().length];
	private final SpriteBatch batcher;
	private final TrackedDevicePose_t.ByReference trackedDevicePosesReference = new TrackedDevicePose_t.ByReference();
    public final TrackedDevicePose_t[] trackedDevicePoses = (TrackedDevicePose_t[]) trackedDevicePosesReference.toArray(VR.k_unMaxTrackedDeviceCount);
    public final Matrix4[] devicePoses = new Matrix4[VR.k_unMaxTrackedDeviceCount];
	
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
			devicePoses[i] = new Matrix4();
		}
		
		system.GetRecommendedRenderTargetSize.apply(scratch, scratch2);
		int width = scratch.get(0);
		int height = scratch2.get(0);
		
		setupEye(Eye.Left, width, height, hasStencil);
		setupEye(Eye.Right, width, height, hasStencil);
		
		batcher = new SpriteBatch();
	}
	
	private void setupEye(Eye eye, int width, int height, boolean hasStencil) {
		buffers[eye.index] = new FrameBuffer(Format.RGBA8888, width, height, true, hasStencil);
		textures[eye.index] = new Texture_t(buffers[eye.index].getColorBufferTexture().getTextureObjectHandle(), VR.EGraphicsAPIConvention.API_OpenGL, VR.EColorSpace.ColorSpace_Gamma);
		regions[eye.index] = new TextureRegion(buffers[eye.index].getColorBufferTexture());
		regions[eye.index].flip(false, true);
		VRCamera camera = cameras[eye.index] = new VRCamera(this, eye);
		camera.near = 0.1f;
		camera.far = 1000f;		
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
		cameras[Eye.Left.index].update();
		cameras[Eye.Right.index].update();
	}
	
	private void updateDevicePoses() {        
        for (int device = 0; device < VR.k_unMaxTrackedDeviceCount; device++) {
            if (trackedDevicePoses[device].bPoseIsValid == 1) {            	
                VRCamera.hmdMat34ToMatrix4(trackedDevicePoses[device].mDeviceToAbsoluteTracking, devicePoses[device]);
           }
        }
    }

	/**
	 * @param deviceIndex device index, use 
	 * @return the device pose (translation, rotation)
	 */
	public Matrix4 getDevicePose(int deviceIndex) {
		if (deviceIndex < 0 || deviceIndex >= devicePoses.length) throw new IndexOutOfBoundsException("Device index must be >= 0 and <= " + devicePoses.length);
		return devicePoses[deviceIndex];
	}
	
	/**
	 * Start rendering to the rendering surface for the given eye.
	 * Complete by calling {@link #endEye()}.
	 */
	public void beginEye(Eye eye) {
		if (!renderingStarted) throw new GdxRuntimeException("Call begin() before calling beginEye()");
		if (currentEye != null) throw new GdxRuntimeException("Last beginEye() call not completed, call endEye() before starting a new render");
		currentEye = eye;
		buffers[eye.index].begin();
	}
	
	/**
	 * Completes rendering to the rendering surface for the given eye.
	 */
	public void endEye() {
		if (currentEye == null) throw new GdxRuntimeException("Call beginEye() before endEye()");
		buffers[currentEye.index].end();
		currentEye = null;		
	}
	
	/**
	 * Completes rendering and submits the rendering surfaces to the
	 * head mounted display.
	 */
	public void end() {
		if (!renderingStarted) throw new GdxRuntimeException("Call begin() before end()");
		renderingStarted = false;
		
		compositor.Submit.apply(VR.EVREye.EYE_Left, textures[Eye.Left.index], null, VR.EVRSubmitFlags.Submit_Default);
		compositor.Submit.apply(VR.EVREye.Eye_Right, textures[Eye.Right.index], null, VR.EVRSubmitFlags.Submit_Default);
		Gdx.gl.glFinish();
		compositor.PostPresentHandoff.apply();		
	}
	
	public void dispose() {
		for (FrameBuffer b: buffers) b.dispose();
		batcher.dispose();
		VR.VR_Shutdown();
	}
	
	/**
	 * Resizes the companion window so the rendering buffers
	 * can be displayed without stretching.
	 */
	public void resizeCompanionWindow() {
		Gdx.graphics.setWindowedMode(buffers[0].getWidth(), buffers[0].getHeight());
	}
	
	/**
	 * Renders the content of the given eye's rendering surface
	 * to the entirety of the companion window.
	 */
	public void renderToCompanionWindow(Eye eye) {
		FrameBuffer buffer = buffers[eye.index];
		TextureRegion region = regions[eye.index];		
		batcher.getProjectionMatrix().setToOrtho2D(0, 0, buffer.getWidth(), buffer.getHeight());
		batcher.begin();
		batcher.draw(region, 0, 0);
		batcher.end();
	}
	
	/**
	 * @return the rendering surface for the given eye.
	 */
	public FrameBuffer getFrameBuffer(Eye eye) {
		return buffers[eye.index];
	}
	
	/**
	 * @return the {@link VRCamera} updated to the latest tracking position for the given eye.
	 */
	public VRCamera getCamera(Eye eye) {
		return cameras[eye.index];
	}
}