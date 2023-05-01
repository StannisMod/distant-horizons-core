package com.seibel.lod.api.methods.events.sharedParameterObjects;

import com.seibel.lod.coreapi.util.math.Mat4f;

/**
 * Parameter passed into Render events.
 *
 * @author James Seibel
 * @version 2022-9-5
 */
public class DhApiRenderParam
{
	/** The projection matrix Minecraft is using to render this frame. */
	public final Mat4f mcProjectionMatrix;
	/** The model view matrix Minecraft is using to render this frame. */
	public final Mat4f mcModelViewMatrix;
	
	/** The projection matrix Distant Horizons is using to render this frame. */
	public final Mat4f dhProjectionMatrix;
	/** The model view matrix Distant Horizons is using to render this frame. */
	public final Mat4f dhModelViewMatrix;
	
	/** Indicates how far into this tick the frame is. */
	public final float partialTicks;
	
	
	
	public DhApiRenderParam(
			Mat4f newMcProjectionMatrix, Mat4f newMcModelViewMatrix,
			Mat4f newDhProjectionMatrix, Mat4f newDhModelViewMatrix,
			float newPartialTicks)
	{
		this.mcProjectionMatrix = newMcProjectionMatrix;
		this.mcModelViewMatrix = newMcModelViewMatrix;
		
		this.dhProjectionMatrix = newDhProjectionMatrix;
		this.dhModelViewMatrix = newDhModelViewMatrix;
		
		this.partialTicks = newPartialTicks;
	}
	
}
