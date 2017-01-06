package com.badlogic.gdx.vr;

import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
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
import vr.VRControllerState_t;
import vr.VREvent_t;

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
	
	/**
	 * Type of a {@link VRDevice}
	 */
	public static enum VRDeviceType {
		/** the head mounted display **/
		HeadMountedDisplay,
		/** a controller like Oculus touch or HTC Vice controller **/
		Controller,
		/** a camera/base station tracking the HMD and/or controllers **/
		BaseStation,
		/** a generic VR tracking device **/
		Generic
	}
	
	/**
	 * The role of a {@link VRDevice} of type {@link VRDeviceType#Controller}
	 */
	public static enum VRControllerRole {
		Unknown,
		LeftHand,
		RightHand
	}
	
	/**
	 * Button ids on VR controllers 
	 */
	public static class VRControllerButtons {
		public static final int System = 0;
		public static final int ApplicationMenu	= 1;
		public static final int Grip = 2;
		public static final int DPad_Left = 3;
		public static final int DPad_Up = 4;
		public static final int DPad_Right = 5;
		public static final int DPad_Down = 6;
		public static final int A = 7;
		
		public static final int ProximitySensor = 31;

		public static final int Axis0 = 32;
		public static final int Axis1 = 33;
		public static final int Axis2 = 34;
		public static final int Axis3 = 35;
		public static final int Axis4 = 36;

		// aliases for well known controllers
		public static final int SteamVR_Touchpad = Axis0;
		public static final int SteamVR_Trigger	= Axis1;

		public static final int Dashboard_Back = Grip;
	}
	
	/**
	 * Axes ids on VR controllers
	 */
	public static class VRControllerAxes {
		public static final int Axis0 = 0;
		public static final int Axis1 = 1;
		public static final int Axis2 = 2;
		public static final int Axis3 = 3;
		public static final int Axis4 = 4;
		
		// aliases for known controllers
		public static final int SteamVR_Touchpad = Axis0;
		public static final int SteamVR_Trigger = Axis1;
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
    
    // devices, their poses and listeners
    private final VRDevicePose[] devicePoses = new VRDevicePose[trackedDevicePoses.length];
    private final VRDevice[] devices = new VRDevice[trackedDevicePoses.length];
    private final Array<VRDeviceListener> listeners = new Array<VRDeviceListener>();
    private final VREvent_t event = new VREvent_t();
    
    // book keeping
	private Eye currentEye = null;
	private boolean renderingStarted = false;
	private boolean initialDevicesReported = false;

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
	 * Adds a {@link VRDeviceListener} to receive events
	 */
	public void addListener(VRDeviceListener listener) {
		this.listeners.add(listener);
	}
	
	/**
	 * Removes a {@link VRDeviceListener}
	 */
	public void removeListener(VRDeviceListener listener) {
		this.listeners.removeValue(listener, true);
	}
	
	/**
	 * @return the first {@link VRDevice} of the given {@link VRDeviceType} or null.
	 */
	public VRDevice getDeviceByType(VRDeviceType type) {
		for (VRDevice d: devices) {
			if (d != null && d.getType() == type) return d;
		}
		return null;
	}
	
	/**
	 * @return all {@link VRDevice} instances of the given {@link VRDeviceType}.
	 */
	public Array<VRDevice> getDevicesByType(VRDeviceType type) {
		Array<VRDevice> result = new Array<VRDevice>();
		for (VRDevice d: devices) {
			if (d != null && d.getType() == type) result.add(d);
		}
		return result;
	}
	
	/**
	 * @return all currently connected {@link VRDevice} instances.
	 */
	public Array<VRDevice> getDevices() {
		Array<VRDevice> result = new Array<VRDevice>();
		for (VRDevice d: devices) {
			if (d != null) result.add(d);
		}
		return result;
	}
	
	/**
	 * @return the {@link VRDevice} of ype {@link VRDeviceType#Controller} that matches the role, or null.
	 */
	public VRDevice getControllerByRole(VRControllerRole role) {
		for (VRDevice d: devices) {
			if (d != null && d.getType() == VRDeviceType.Controller && d.getControllerRole() == role) return d;
		}
		return null;
	}
	
	VRDevicePose getDevicePose(int deviceIndex) {
		if (deviceIndex < 0 || deviceIndex >= devicePoses.length) throw new IndexOutOfBoundsException("Device index must be >= 0 and <= " + devicePoses.length);
		return devicePoses[deviceIndex];
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
		
		if (!initialDevicesReported) {
			for (int index = 0; index < devices.length; index++) {
				if (system.IsTrackedDeviceConnected.apply(index) != 0) {
					createDevice(index);        		
		    		for (VRDeviceListener l: listeners) {
		    			l.connected(devices[index]);
		    		}
				}
			}
			initialDevicesReported = true;
		}
		updateDevices();
		
		perEyeData[Eye.Left.index].cameras.update();
		perEyeData[Eye.Right.index].cameras.update();
	}
	
	private void updateDevices() {        
        for (int device = 0; device < VR.k_unMaxTrackedDeviceCount; device++) {
			TrackedDevicePose_t trackedPose = trackedDevicePoses[device];
			VRDevicePose pose = devicePoses[device];
			
			hmdMat34ToMatrix4(trackedPose.mDeviceToAbsoluteTracking, pose.transform);
			pose.velocity.set(trackedPose.vVelocity.v);
			pose.angularVelocity.set(trackedPose.vAngularVelocity.v);
			pose.isConnected = trackedPose.bDeviceIsConnected != 0;
			pose.isValid = trackedPose.bPoseIsValid != 0;
        }
        
        while (system.PollNextEvent.apply(event, event.size()) != 0) {
        	int index = event.trackedDeviceIndex;
        	if (index < 0 || index > trackedDevicePoses.length) continue;        	        
        	int button = 0;
        	
        	switch (event.eventType) {
        	case VR.EVREventType.VREvent_TrackedDeviceActivated:        		
        		createDevice(index);        		
        		for (VRDeviceListener l: listeners) {
        			l.connected(devices[index]);
        		}        		
        		break;
        	case VR.EVREventType.VREvent_TrackedDeviceDeactivated:
        		index = event.trackedDeviceIndex;
        		if (devices[index] == null) continue;
        		for (VRDeviceListener l: listeners) {
        			l.disconnected(devices[index]);
        		}
        		devices[index] = null;        		
        		break;        	        
        	case VR.EVREventType.VREvent_ButtonPress:
        		if (devices[index] == null) continue;
        		event.data.setType("controller");
        		event.data.read();
        		button = event.data.controller.button;
        		devices[index].setButton(button, true);
        		for (VRDeviceListener l: listeners) {
        			l.buttonPressed(devices[index], button);
        		}
        		break;
        	case VR.EVREventType.VREvent_ButtonUnpress:
        		if (devices[index] == null) continue;
        		event.data.setType("controller");
        		event.data.read();        		
        		button = event.data.controller.button;
        		devices[index].setButton(button, false);
        		for (VRDeviceListener l: listeners) {
        			l.buttonReleased(devices[index], button);
        		}        		
        		break;        	   
        	}
        }
    }
	
	private void createDevice(int index) {
		VRDeviceType type = null;
		int deviceClass = system.GetTrackedDeviceClass.apply(index);        		        		
		switch(deviceClass) {
		case VR.ETrackedDeviceClass.TrackedDeviceClass_HMD: 
			type = VRDeviceType.HeadMountedDisplay; 
			break;
		case VR.ETrackedDeviceClass.TrackedDeviceClass_Controller:
			type = VRDeviceType.Controller;
			break;
		case VR.ETrackedDeviceClass.TrackedDeviceClass_TrackingReference:
			type = VRDeviceType.BaseStation;
			break;
		case VR.ETrackedDeviceClass.TrackedDeviceClass_Other:
			type = VRDeviceType.Generic;
			break;
		default:
			return;
		}
		
		VRControllerRole role = VRControllerRole.Unknown;
		if (type == VRDeviceType.Controller) {
			int r =  system.GetControllerRoleForTrackedDeviceIndex.apply(index);
			switch(r) {
			case VR.ETrackedControllerRole.TrackedControllerRole_LeftHand:
				role = VRControllerRole.LeftHand;
				break;
			case VR.ETrackedControllerRole.TrackedControllerRole_RightHand:
				role = VRControllerRole.RightHand;
				break;      				
			}
		}
		devices[index] = new VRDevice(devicePoses[index], type, role); 
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
		private final VRDeviceType type;
		private VRControllerRole role;
		private long buttons = 0;
		private final VRControllerState_t state = new VRControllerState_t();
		
		VRDevice(VRDevicePose pose, VRDeviceType type, VRControllerRole role) {
			this.pose = pose;
			this.type = type;
			this.role = role;
		}			

		/**
		 * @return the most up-to-date {@link VRDevicePose}
		 */
		public VRDevicePose getPose() {
			return pose;
		}
		
		/**
		 * @return the {@link VRDeviceType}
		 */
		public VRDeviceType getType() {
			return type;
		}
		
		/**
		 * The {@link VRControllerRole}, indicating if the {@link VRDevice} is assigned
		 * to the left or right hand.
		 * 
		 * <p>
		 * <strong>Note</strong>: the role is not reliable! If one controller is connected on
		 * startup, it will have a role of {@link VRControllerRole#Unknown} and retain
		 * that role even if a second controller is connected (which will also haven an
		 * unknown role). The role is only reliable if two controllers are connected
		 * already, and none of the controllers disconnects during the application
		 * life-time.</br>
		 * At least on the HTC Vive, the first connected controller is always the right hand
		 * and the second connected controller is the left hand. The order stays the same
		 * even if controllers disconnect/reconnect during the application life-time.
		 * </p>
		 */
		public VRControllerRole getControllerRole() {			
			return role;
		}
		
		void setControllerRole(VRControllerRole role) {		
			this.role = role;
		}
		
		/**
		 * @return whether the device is connected
		 */
		public boolean isConnected() {
			return system.IsTrackedDeviceConnected.apply(pose.index) != 0;
		}
		
		/**
		 * @return whether the button from {@link VRControllerButtons} is pressed
		 */
		public boolean isButtonPressed(int button) {
			if (button < 0 || button >= 64) return false;
			return (buttons & (1l << button)) != 0;  
		}
		
		void setButton(int button, boolean pressed) {
			if (pressed) {
				buttons |= (1l << button); 
			} else {
				buttons &= (1l << button);
			}
		}
		
		/**
		 * @return the x-coordinate in the range [-1, 1] of the given axis from {@link VRControllerAxes}
		 */
		public float getAxisX(int axis) {
			if (axis < 0 || axis >= 5) return 0; 
			system.GetControllerState.apply(pose.index, state);
			return state.rAxis[axis].x;
		}
		
		/**
		 * @return the y-coordinate in the range [-1, 1] of the given axis from {@link VRControllerAxes}
		 */
		public float getAxisY(int axis) {
			if (axis < 0 || axis >= 5) return 0; 
			system.GetControllerState.apply(pose.index, state);
			return state.rAxis[axis].y;
		}
		
		/**
		 * Trigger a haptic pulse (vibrate) for the duration in microseconds. Subsequent calls
		 * to this method within 5ms will be ignored.
		 * @param duration pulse duration in microseconds
		 */
		public void triggerHapticPulse(short duration) {
			system.TriggerHapticPulse.apply(pose.index, 0, duration);
		}
		
		@Override
		public String toString() {
			return "VRDevice[index=" + pose.index + ", type=" + type + ", role=" + role + "]";
		}
	}
	
	public interface VRDeviceListener {
		/** A new {@link VRDevice} has connected **/		 
		void connected(VRDevice device);
		
		/** A {@link VRDevice} has disconnected **/
		void disconnected(VRDevice device);			
		
		/** A button from {@link VRControllerButtons} was pressed on the {@link VRDevice} **/
		void buttonPressed(VRDevice device, int button);
		
		/** A button from {@link VRControllerButtons} was released on the {@link VRDevice} **/
		void buttonReleased(VRDevice device, int button);
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