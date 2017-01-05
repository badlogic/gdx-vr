package com.badlogic.gdx.vr;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.vr.VRContext.Eye;

import vr.HmdMatrix34_t;
import vr.HmdMatrix44_t.ByValue;
import vr.VR;

/**
 * A {@link Camera} implementation for one {@link Eye}
 * of a {@link VRContext}. All properties except {@link Camera#near},
 * {@link Camera#far} and {@link #offset} will be overwritten
 * on every call to {@link #update()} based on the tracked values
 * from the head mounted display. The {@link #offset} 
 * vector allows you to position the camera in world space.
 * @author badlogic
 *
 */
public class VRCamera extends Camera {
	public final VRContext context;
	public final Eye eye;
	public final Matrix4 eyeSpace = new Matrix4();
	public final Matrix4 invEyeSpace = new Matrix4();	
	public final Vector3 offset = new Vector3();

	public VRCamera(VRContext context, Eye eye) {
		this.context = context;
		this.eye = eye;
	}
	
	@Override
	public void update() {
		update(true);		
	}

	@Override
	public void update(boolean updateFrustum) {
		// get the projection matrix from the HDM
		ByValue projectionMat = context.system.GetProjectionMatrix.apply(eye.index, near, far, VR.EGraphicsAPIConvention.API_OpenGL);
		VRContext.hmdMat4toMatrix4(projectionMat, projection);
		
		// get the eye space matrix from the HDM
		HmdMatrix34_t.ByValue eyeMat = context.system.GetEyeToHeadTransform.apply(eye.index);
		VRContext.hmdMat34ToMatrix4(eyeMat, eyeSpace);
		invEyeSpace.set(eyeSpace).inv();
		 
		// get the pose matrix from the HDM
		view.set(context.getDevicePose(VRContext.HMD_DEVICE_INDEX).transform).inv();
		
		// FIXME set position, direction, up etc. based on eye + view matrix
		
		combined.set(projection);
		Matrix4.mul(combined.val, invEyeSpace.val);
		Matrix4.mul(combined.val, view.val);
		
		if (updateFrustum) {
			invProjectionView.set(combined);
			Matrix4.inv(invProjectionView.val);
			frustum.update(invProjectionView);
		}
	}	
}
