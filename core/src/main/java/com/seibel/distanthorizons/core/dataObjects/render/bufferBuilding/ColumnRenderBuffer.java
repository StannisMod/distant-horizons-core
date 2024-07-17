/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.*;

/**
 * Java representation of one or more OpenGL buffers for rendering.
 *
 * @see ColumnRenderBufferBuilder
 */
public class ColumnRenderBuffer implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper minecraftClient = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static final long MAX_BUFFER_UPLOAD_TIMEOUT_NANOSECONDS = 1_000_000;
	
	/** number of bytes a single quad takes */
	public static final int QUADS_BYTE_SIZE = LodUtil.LOD_VERTEX_FORMAT.getByteSize() * 4;
	/** how big a single VBO can be in bytes */
	public static final int MAX_VBO_BYTE_SIZE = 1024 * 1024; // 1 MB
	public static final int MAX_QUADS_PER_BUFFER = MAX_VBO_BYTE_SIZE / QUADS_BYTE_SIZE;
	public static final int FULL_SIZED_BUFFER = MAX_QUADS_PER_BUFFER * QUADS_BYTE_SIZE;
	
	
	
	public final DhBlockPos pos;
	
	public boolean buffersUploaded = false;
	
	public SharedVbo.BufferSlice[] opaqueBufferSlices;
	public SharedVbo.BufferSlice[] transparentBufferSlices;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public ColumnRenderBuffer(DhBlockPos pos)
	{
		this.pos = pos;
		this.opaqueBufferSlices = new SharedVbo.BufferSlice[0];
		this.transparentBufferSlices = new SharedVbo.BufferSlice[0];
	}
	
	
	
	//==================//
	// buffer uploading //
	//==================//
	
	/** Should be run on a DH thread. */
	public void uploadBuffer(LodQuadBuilder builder, EDhApiGpuUploadMethod gpuUploadMethod) throws InterruptedException
	{
		LodUtil.assertTrue(Thread.currentThread().getName().startsWith(ThreadUtil.THREAD_NAME_PREFIX), "Buffer uploading needs to be done on a DH thread to prevent locking up any MC threads.");
		
		
		// upload on MC's render thread
		CompletableFuture<Void> uploadFuture = new CompletableFuture<>();
		minecraftClient.executeOnRenderThread(() ->
		{
			try
			{
				this.uploadBuffersUsingUploadMethod(builder, gpuUploadMethod);
				uploadFuture.complete(null);
			}
			catch (InterruptedException e)
			{
				throw new CompletionException(e);
			}
		});
		
		
		try
		{
			// wait for the upload to finish
			uploadFuture.get(5_000, TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException e)
		{
			LOGGER.warn("Error uploading builder ["+builder+"] synchronously. Error: "+e.getMessage(), e);
		}
		catch (TimeoutException e)
		{
			// timeouts can be ignored because it generally means the
			// MC Render thread executor was closed 
			//LOGGER.warn("Error uploading builder ["+builder+"] synchronously. Error: "+e.getMessage(), e);
		}
	}
	private void uploadBuffersUsingUploadMethod(LodQuadBuilder builder, EDhApiGpuUploadMethod gpuUploadMethod) throws InterruptedException
	{
		//if (gpuUploadMethod.useEarlyMapping)
		//{
		//	this.uploadBuffersMapped(builder, gpuUploadMethod);
		//}
		//else
		//{
			this.uploadBuffersDirect(builder);
		//}
		
		this.buffersUploaded = true;
	}
	
	
	
	//private void uploadBuffersMapped(LodQuadBuilder builder, EDhApiGpuUploadMethod method)
	//{
	//	// opaque vbos //
	//	
	//	this.vbos = ColumnRenderBufferBuilder.resizeBuffer(this.vbos, builder.getCurrentNeededOpaqueVertexBufferCount());
	//	for (int i = 0; i < this.vbos.length; i++)
	//	{
	//		if (this.vbos[i] == null)
	//		{
	//			this.vbos[i] = new GLVertexBuffer(method.useBufferStorage);
	//		}
	//	}
	//	LodQuadBuilder.BufferFiller func = builder.makeOpaqueBufferFiller(method);
	//	for (GLVertexBuffer vbo : this.vbos)
	//	{
	//		func.fill(vbo);
	//	}
	//	
	//	
	//	// transparent vbos //
	//	
	//	this.vbosTransparent = ColumnRenderBufferBuilder.resizeBuffer(this.vbosTransparent, builder.getCurrentNeededTransparentVertexBufferCount());
	//	for (int i = 0; i < this.vbosTransparent.length; i++)
	//	{
	//		if (this.vbosTransparent[i] == null)
	//		{
	//			this.vbosTransparent[i] = new GLVertexBuffer(method.useBufferStorage);
	//		}
	//	}
	//	LodQuadBuilder.BufferFiller transparentFillerFunc = builder.makeTransparentBufferFiller(method);
	//	for (GLVertexBuffer vbo : this.vbosTransparent)
	//	{
	//		transparentFillerFunc.fill(vbo);
	//	}
	//}
	
	private void uploadBuffersDirect(LodQuadBuilder builder)
	{
		int opaqueSliceCount = builder.getCurrentNeededOpaqueVertexBufferCount();
		if (this.opaqueBufferSlices.length != opaqueSliceCount)
		{
			SharedVbo.OPAQUE.deallocate(this.opaqueBufferSlices);
			this.opaqueBufferSlices = new SharedVbo.BufferSlice[opaqueSliceCount];
		}
		uploadBuffersDirect(SharedVbo.OPAQUE, this.opaqueBufferSlices, builder.makeOpaqueVertexBuffers());
		
		
		int transparentSliceCount = builder.getCurrentNeededTransparentVertexBufferCount();
		if (this.transparentBufferSlices.length != transparentSliceCount)
		{
			SharedVbo.TRANSPARENT.deallocate(this.transparentBufferSlices);
			this.transparentBufferSlices = new SharedVbo.BufferSlice[transparentSliceCount];
		}
		uploadBuffersDirect(SharedVbo.TRANSPARENT, this.transparentBufferSlices, builder.makeTransparentVertexBuffers());
	}
	
	private static void uploadBuffersDirect(SharedVbo handler, SharedVbo.BufferSlice[] bufferSlices, Iterator<ByteBuffer> iter)
	{
		int i = 0;
		while (iter.hasNext())
		{
			if (i >= bufferSlices.length)
			{
				throw new RuntimeException("Too many vertex buffers!!");
			}
			
			// get or create the buffer
			if (bufferSlices[i] == null)
			{
				bufferSlices[i] = handler.allocateSlice();
			}
			SharedVbo.BufferSlice slice = bufferSlices[i];
			
			
			ByteBuffer buffer = iter.next();
			try
			{
				handler.updateBuffer(slice, buffer);
			}
			catch (Exception e)
			{
				bufferSlices[i] = null;
				handler.deallocate(slice);
				LOGGER.error("Failed to upload buffer. Error: ["+e.getMessage()+"].", e);
			}
			
			i++;
		}
		
		if (i < bufferSlices.length)
		{
			throw new RuntimeException("Too few buffer chunks!");
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	//public void renderOpaque(LodRenderer renderContext, DhApiRenderParam renderEventParam)
	//{
	//	renderContext.setModelViewMatrixOffset(this.pos, renderEventParam);
	//	renderContext.drawSharedVbo(SharedVbo.OPAQUE, this.opaqueBufferSlices);
	//}
	//
	//public void renderTransparent(LodRenderer renderContext, DhApiRenderParam renderEventParam)
	//{
	//	renderContext.setModelViewMatrixOffset(this.pos, renderEventParam);
	//	renderContext.drawSharedVbo(SharedVbo.TRANSPARENT, this.transparentBufferSlices);
	//}
	
	
	
	//==============//
	// misc methods //
	//==============//
	
	// TODO
	/** can be used when debugging */
	public boolean hasNonNullVbos() { return false; } //this.vbos != null || this.vbosTransparent != null; }
	
	/** can be used when debugging */
	public int bufferSliceCount() 
	{
		int count = 0;
		
		if (this.opaqueBufferSlices != null)
		{
			count += this.opaqueBufferSlices.length;
		}

		if (this.transparentBufferSlices != null)
		{
			count += this.transparentBufferSlices.length;
		}
		
		return count;
	}
	
	/**
	 * This method is called when object is no longer in use.
	 * Called either after uploadBuffers() returned false (On buffer Upload
	 * thread), or by others when the object is not being used. (not in build,
	 * upload, or render state). 
	 */
	@Override
	public void close()
	{
		this.buffersUploaded = false;
		
		SharedVbo.OPAQUE.deallocate(this.opaqueBufferSlices);
		SharedVbo.TRANSPARENT.deallocate(this.transparentBufferSlices);
	}
	
}
