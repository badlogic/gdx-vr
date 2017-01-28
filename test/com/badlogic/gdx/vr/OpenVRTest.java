package com.badlogic.gdx.vr;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.openvr.VR.*;
import static org.lwjgl.openvr.VRSystem.*;
import static org.lwjgl.openvr.VRCompositor.*;

import java.nio.Buffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL;
import org.lwjgl.openvr.OpenVR;
import org.lwjgl.openvr.Texture;
import org.lwjgl.openvr.TrackedDevicePose;
import org.lwjgl.openvr.VR;

import com.badlogic.gdx.utils.BufferUtils;

public class OpenVRTest {
	
	public static class FBO {
		int width;
		int height;
		int handle;
		int colorTextureHandle;
		
		public FBO(int width, int height) {
			this.width = width;
			this.height = height;
			
			handle = glGenBuffers();
			glBindFramebuffer(GL_FRAMEBUFFER, handle);
			
			colorTextureHandle = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, colorTextureHandle);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTextureHandle, 0);
			
			int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
			if (status != GL_FRAMEBUFFER_COMPLETE) {
				throw new RuntimeException("Couldn't create framebuffer");
			}
			
			glBindFramebuffer(GL_FRAMEBUFFER, 0);
		}
	}
	
	static FBO left;
	static FBO right;
	static Texture textureLeft;
	static Texture textureRight;
	static TrackedDevicePose.Buffer trackedDevicePoses = TrackedDevicePose.create(VR.k_unMaxTrackedDeviceCount);
	static TrackedDevicePose.Buffer trackedDeviceGamePoses = TrackedDevicePose.create(VR.k_unMaxTrackedDeviceCount);
	
	public static void setupVR () {
		IntBuffer error = BufferUtils.newIntBuffer(1);
		int token = VR_InitInternal(error, EVRApplicationType_VRApplication_Scene);
		if (error.get(0) != EVRInitError_VRInitError_None) {
			throw new RuntimeException("Couldn't init VR");
		}
		OpenVR.create(token);
		
		VR_GetGenericInterface(IVRSystem_Version, error);
		if (error.get(0) != EVRInitError_VRInitError_None) {
			throw new RuntimeException("Couldn't init VR");
		}
		
		VR_GetGenericInterface(IVRCompositor_Version, error);
		if (error.get(0) != EVRInitError_VRInitError_None) {
			throw new RuntimeException("Couldn't init VR");
		}
		
		IntBuffer width = BufferUtils.newIntBuffer(1);
		IntBuffer height = BufferUtils.newIntBuffer(1);
		VRSystem_GetRecommendedRenderTargetSize(width, height);
		left = new FBO(width.get(0), height.get(0));
		right = new FBO(width.get(0), height.get(0));
		textureLeft = Texture.create();
		textureRight = Texture.create();
	}
	
	public static void updateVR () {
		VRCompositor_WaitGetPoses(trackedDevicePoses, trackedDeviceGamePoses);
	}
	
	public static void renderVR () {
		glBindFramebuffer(GL_FRAMEBUFFER, left.handle);
		glViewport(0, 0, left.width, left.height);
		glClearColor(1, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);

		glBindFramebuffer(GL_FRAMEBUFFER, right.handle);
		glViewport(0, 0, right.width, right.height);
		glClearColor(1, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		
		textureLeft.set(left.colorTextureHandle, ETextureType_TextureType_OpenGL, EColorSpace_ColorSpace_Gamma);
		VRCompositor_Submit(EVREye_Eye_Left, textureLeft, null, EVRSubmitFlags_Submit_Default);
		textureRight.set(right.colorTextureHandle, ETextureType_TextureType_OpenGL, EVRSubmitFlags_Submit_Default);
		VRCompositor_Submit(EVREye_Eye_Right, textureRight, null, EVRSubmitFlags_Submit_Default);
	}
	
	public static void main (String[] args) {
		if (!glfwInit()) {
			System.out.println("Couldn't init GLFW");
			System.exit(0);
		}
		
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		
		long window = glfwCreateWindow(800, 600, "OpenVR + GLFW", 0, 0);
		if (window == 0) {
			System.out.println("Couldn't create window");
			System.exit(0);
		}
		
		glfwMakeContextCurrent(window);
		glfwSwapInterval(0);
		
		GL.createCapabilities();
		setupVR();
		
		while (!glfwWindowShouldClose(window)) {
			updateVR();
			renderVR();
			
			glfwPollEvents();
			glClearColor(0, 0, 1, 0);
			glClear(GL_COLOR_BUFFER_BIT);
			glfwSwapBuffers(window);
		}
		
		glfwDestroyWindow(window); 
		glfwTerminate();
	}
}
