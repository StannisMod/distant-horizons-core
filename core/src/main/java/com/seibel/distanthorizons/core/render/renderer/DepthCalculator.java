package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL46;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.function.Function;

public class DepthCalculator
{
	
	public static DepthCalculator INSTANCE = new DepthCalculator();
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	private float[] mcOpaqueDepthTextureValues = new float[1];
	private float[] mcTransparentDepthTextureValues = new float[1];
	private float[] dhDepthTextureValues = new float[1];
	private float[] outDepthTextureValues = new float[1];
	private float lastClosestDhDepth = 1.0f;
	
	public float actualMcBlockDistance = 8 * 16; // needs to be non-zero to start DH rendering
	@Deprecated // Replace with thread pool and an AtomicBool for running state
	public Thread thread = null;
	public boolean pause = false;
	
	private boolean gotDhDepthThisFrame = false;
	private boolean gotMcDepthThisFrame = false;
	
	
	//=============//
	// constructor //
	//=============//
	
	private DepthCalculator() { }
	
	
	//======//
	// test //
	//======//
	
	public void getMcOpaqueDepthTexture() { this.gotMcDepthThisFrame = this.trySetDepthTexture(MC_RENDER.getDepthTextureId(), this.mcOpaqueDepthTextureValues); }
	public void getMcTransparentDepthTexture() { this.gotMcDepthThisFrame = this.trySetDepthTexture(MC_RENDER.getDepthTextureId(), this.mcTransparentDepthTextureValues); }
	public void trySetDhDepthTexture() { this.gotDhDepthThisFrame = this.trySetDepthTexture(LodRenderer.INSTANCE.getActiveDepthTextureId(), this.dhDepthTextureValues); }
	private boolean trySetDepthTexture(int id, float[] outputRef)
	{
		// don't change the texture if a process is already running
		if (this.thread != null)
		{
			return false;
		}
		
		this.resizeTexturesIfNeeded();
		if (id == -1)
		{
			return false;
		}
		
		// FIXME this is slow and causes frame stuttering
		GL46.glGetTextureImage(id, 0, GL32.GL_DEPTH_COMPONENT, GL32.GL_FLOAT, outputRef);
		return true;
	}
	private void resizeTexturesIfNeeded()
	{
		int width = MC_RENDER.getTargetFramebufferViewportWidth();
		int height = MC_RENDER.getTargetFramebufferViewportHeight();
		int elementCount = width * height;
		if (this.dhDepthTextureValues.length != elementCount)
		{
			this.mcOpaqueDepthTextureValues = new float[width * height];
			this.mcTransparentDepthTextureValues = new float[width * height];
			
			this.dhDepthTextureValues = new float[width * height];
			this.outDepthTextureValues = new float[width * height];
		}
	}
	
	
	
	public void tryCalculateAsync()
	{
		if (this.thread != null)
		{
			return;
		}
		
		//if (!this.gotDhDepthThisFrame
		//	|| !this.gotMcDepthThisFrame)
		//{
		//	return;
		//}
		
		if (this.pause)
		{
			int k = 0;
		}
		
		
		this.thread = new Thread(() ->
		{
			try
			{
				this.calculateDepth();
			}
			catch (Exception e)
			{
				LOGGER.error("async test: " + e.getMessage(), e);
			}
			finally
			{
				this.thread = null;
				this.pause = false;
			}
		});
		this.thread.start();
	}
	public void calculateDepth()
	{
		int width = MC_RENDER.getTargetFramebufferViewportWidth();
		int height = MC_RENDER.getTargetFramebufferViewportHeight();
		int elementCount = width * height;
		
		
		// used to calculate the DH render matrices
		RenderParams renderParams =
			new RenderParams(
				EDhApiRenderPass.OPAQUE,
				ClientApi.RENDER_STATE.frameTime,
				ClientApi.RENDER_STATE.mcProjectionMatrix, ClientApi.RENDER_STATE.mcModelViewMatrix,
				ClientApi.RENDER_STATE.clientLevelWrapper
			);
		
		Mat4f dhInvProj = new Mat4f(renderParams.dhProjectionMatrix);
		dhInvProj.invert();
		Matrix4f dhInvProjJoml = dhInvProj.createJomlMatrix();
		
		Mat4f dhInvMvm = new Mat4f(renderParams.dhModelViewMatrix);
		dhInvMvm.invert();
		Matrix4f dhInvMvmJoml = dhInvMvm.createJomlMatrix(); // TODO can we use JOML for MC 1.16?
		
		
		float[] sampledDistances = new float[9];
		
		// find the closest depth value MC hasn't drawn to
		float closestDhDepth = 1.0f; float closestDhDistance = Float.MAX_VALUE;
		int closeUIndex = 0; int closeVIndex = 0;
		for (int u = 0; u < width; u++) // x
		{
			for (int v = 0; v < height; v++) // y
			{
				int invertedV = height - 1 - v;
				int i = (invertedV * width) + u;
				
				
				this.outDepthTextureValues[i] = 0.0f;
				
				float mcDepth = Config.Client.dynamicFadeUseOpaqueMcDepth.get()
					? this.mcOpaqueDepthTextureValues[i]
					: this.mcTransparentDepthTextureValues[i];
				if (mcDepth < 1.0f) // ignore positions MC has drawn to
				{
					continue;
				}
				
				float dhDepth = this.dhDepthTextureValues[i];
				if (dhDepth == 1.0f) // ignore positions DH has NOT drawn to
				{
					continue;
				}
				this.outDepthTextureValues[i] = dhDepth;
				
				
				// calculate this point's distance from the camera
				float ndcU = u / (float)width;
				float ndcV = invertedV / (float)height;
				float dist = convertDepthToBlockDistance(ndcU, ndcV, dhInvProjJoml, dhInvMvmJoml, dhDepth);
				
				//// sample the 9 surrounding pixels to account for off-by-one errors between the MC and DH depth textures
				//int sampleIndex = 0;
				//for (int relU = -1; relU < 1; relU++)
				//{
				//	for (int relV = -1; relV < 1; relV++)
				//	{
				//		invertedV = height - 1 - v + relV;
				//		i = (invertedV * width) + u + relU;
				//		dhDepth = this.dhDepthTextureValues[i];
				//		
				//		if (v + relV < 0 || v + relV > height
				//			|| u + relU < 0 || u + relU > width)
				//		{
				//			sampledDistances[sampleIndex] = 0.0f;
				//			sampleIndex++;
				//		}
				//		
				//		// calculate this point's distance from the camera
				//		float ndcU = u / (float)width;
				//		float ndcV = invertedV / (float)height;
				//		float dist = convertDepthToBlockDistance(ndcU, ndcV, dhInvProjJoml, dhInvMvmJoml, dhDepth);
				//		
				//		sampledDistances[sampleIndex] = dist;
				//		sampleIndex++;
				//	}
				//}
				//
				//
				//Arrays.sort(sampledDistances);
				//// return the median element
				//float dist = sampledDistances[sampledDistances.length / 2];
				//if (dist == 0.0f)
				//{
				//	// the median was 0, return the smallest non-zero element
				//	for (sampleIndex = 0; sampleIndex < sampledDistances.length; sampleIndex++)
				//	{
				//		if (sampledDistances[sampleIndex] != 0.0f)
				//		{
				//			dist = sampledDistances[sampleIndex];
				//			break;
				//		}
				//	}
				//}
				
				if (dist < closestDhDistance)
				{
					closestDhDepth = dhDepth;
					closestDhDistance = dist;
					
					closeUIndex = u;
					closeVIndex = invertedV;
				}
			}
		}
		
		
		
		
		if (this.lastClosestDhDepth != closestDhDepth)
		{
			NumberFormat numForm = NumberFormat.getNumberInstance();
			ClientApi.INSTANCE.showChatMessageNextFrame(
				"closest: ["+numForm.format(closestDhDepth)+"]-b["+numForm.format(closestDhDistance)+"]c["+numForm.format(closestDhDistance/16)+"] ("+(closeUIndex)+","+(closeVIndex)+")" +
					"");
			
			this.actualMcBlockDistance = closestDhDistance;
		}
		this.lastClosestDhDepth = closestDhDepth;
		
		
		if (this.pause)
		{
			// find the range of depth values used by both textures for clearer exporting
			float closestMcDepth = 1.0f; float furthestMcDepth = 0.0f;
			float minDhDepth = 1.0f; float maxDhDepth = 0.0f;
			for (int i = 0; i < elementCount; i++)
			{
				float mcDepth = this.mcOpaqueDepthTextureValues[i];
				if (mcDepth != 0.0f && mcDepth != 1.0f)
				{
					if (mcDepth < closestMcDepth)
					{
						closestMcDepth = mcDepth;
					}
					
					if (mcDepth > furthestMcDepth)
					{
						furthestMcDepth = mcDepth;
					}
				}
				
				float dhDepth = this.dhDepthTextureValues[i];
				if (dhDepth != 0.0f && dhDepth != 1.0f)
				{
					if (dhDepth < minDhDepth)
					{
						minDhDepth = dhDepth;
					}
					
					if (dhDepth > maxDhDepth)
					{
						maxDhDepth = dhDepth;
					}
				}
				
			}
			
			
			String exportPath = Config.Client.dynamicFadeExportPath.get();
			
			ClientApi.INSTANCE.showChatMessageNextFrame("exporting debug textures to: ["+exportPath+"]...");
			
			// mc opaque
			createImg(
				this.mcOpaqueDepthTextureValues,
				closeUIndex, closeVIndex,
				(Float depth) -> { return null; },
				width, height, closestMcDepth, furthestMcDepth,
				exportPath+"mc-opaque_depth.png");
			
			// mc transparent
			createImg(
				this.mcTransparentDepthTextureValues,
				closeUIndex, closeVIndex,
				(Float depth) -> { return null; },
				width, height, closestMcDepth, furthestMcDepth,
				exportPath+"mc-tran_depth.png");
			
			// dh
			Function<Float, Color> customColorFunc = (Float depth) ->
			{
				if (depth == this.lastClosestDhDepth)
				{
					return Color.RED;
				}
				//else if (depth <= (lastClosestDhDepth + 0.01f))
				//{
				//	return Color.ORANGE;
				//}
				
				return null;
			};
			createImg(
				this.dhDepthTextureValues,
				closeUIndex, closeVIndex,
				customColorFunc,
				width, height, minDhDepth, maxDhDepth,
				exportPath+"dh_depth.png");
			
			// temp
			
			createImg(
				this.outDepthTextureValues,
				closeUIndex, closeVIndex,
				(Float depth) -> { return null; },
				width, height, minDhDepth, maxDhDepth,
				exportPath+"temp_depth.png");
			
			int breakPoint = 0;
		}
	}
	/** NDC (Normalized Device Coordinates) must be between 0.0 and 1.0 (inclusive) */
	private static float convertDepthToBlockDistance(float ndcU, float ndcV, Matrix4f invProj, Matrix4f invMvm, float depth)
	{
		// This assumes depth is scaled to [0, 1]
		// Transform depth to clip space Z value
		float z = depth * 2.0f - 1.0f;
		
		// Create a vector in clip space
		Vector4f clipSpacePosition = new Vector4f(ndcU, ndcV, z, 1.0f);
		
		// Transform to world space
		Vector4f worldSpacePosition = clipSpacePosition.mul(invProj);
		worldSpacePosition.div(worldSpacePosition.w); // Perform perspective divide
		
		// Finally apply the inverse model-view matrix to get world space coordinates
		worldSpacePosition = worldSpacePosition.mul(invMvm);
		
		// calculate distance from the camera
		float distance = worldSpacePosition.distance(0, 0, 0, 0);
		return distance;
	}
	private static void createImg(
		float[] tex,
		int nearestU, int nearestV,
		Function<Float, Color> customColorFunc,
		int width, int height,
		float minDepth, float maxDepth,
		String filePath)
	{
		// Create a BufferedImage
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		for (int u = 0; u < width; u++)
		{
			for (int v = 0; v < height; v++)
			{
				int invertedY = height - 1 - v;
				
				// Normalize the depth value to a grayscale pixel
				float depthValue = tex[(invertedY * width) + u];
				Color color = customColorFunc.apply(depthValue);
				if (color == null)
				{
					float normalizedDepth = (depthValue - minDepth) / (maxDepth - minDepth); // Normalize to 0.0 to 1.0
					normalizedDepth = Math.max(0.0f, Math.min(1.0f, normalizedDepth)); // Clamp to valid range
					
					int gray = (int) (normalizedDepth * 255); // Map to 0-255
					gray = Math.max(0, Math.min(255, gray)); // Clamp to valid range
					color = new Color(gray, gray, gray);
				}
				
				if (depthValue == 1.0f)
				{
					color = new Color(0,0,0,0);
				}
				
				if (u == nearestU)
				{
					color = Color.MAGENTA;
				}
				else if (invertedY == nearestV)
				{
					color = Color.ORANGE;
				}
				
				image.setRGB(u, v, color.getRGB());
			}
		}
		
		// Write the image to a file
		try
		{
			ImageIO.write(image, "png", new File(filePath));
		}
		catch (IOException e)
		{
			LOGGER.error("Unable to write texture to file, error: ["+e.getMessage()+"].", e);
		}
	}
	
	
	
	
}
