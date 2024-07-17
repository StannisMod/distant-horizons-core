package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/** Used to allow multiple {@link ColumnRenderBuffer}'s to be put in a single VBO. */
public class SharedVbo 
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final SharedVbo OPAQUE = new SharedVbo(1_000_000_000/*1GB*/, 1024 * 1024/*1MB*/);
	public static final SharedVbo TRANSPARENT = new SharedVbo(1_000_000_000/*1GB*/, 1024 * 1024/*1MB*/);
	
	
	public final int vboId;
	
	
	private final long bufferTotalByteSize;
	/** the length of a single chunk of this VBO in bytes. */
	private final int chunkByteSize;
	
	private final ConcurrentHashMap<Long, BufferSlice> bufferSliceByStartingIndex = new ConcurrentHashMap<>();
	private long nextMemoryAddress = 0L;
	private final Queue<BufferSlice> availableSlices = new ArrayDeque<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public SharedVbo(long totalSize, int chunkByteSize) 
	{
		LodUtil.assertTrue(GLProxy.getInstance().runningOnRenderThread(), "Buffer Handler has to be created on the render thread.");
		
		this.bufferTotalByteSize = totalSize;
		this.chunkByteSize = chunkByteSize;
		
		// Generate and bind the VBO
		this.vboId = GL32.glGenBuffers();
		GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.vboId);
		GL32.glBufferData(GL32.GL_ARRAY_BUFFER, this.bufferTotalByteSize, GL32.GL_DYNAMIC_DRAW);
		GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, 0);
	}
	
	
	
	//============//
	// allocation //
	//============//
	
	@Nullable
	public SharedVbo.BufferSlice allocateSlice()
	{
		BufferSlice availableSlice = this.availableSlices.poll();
		if (availableSlice != null)
		{
			return availableSlice;
		}
		
		// Find the first free chunk
		for (long startingIndex = this.nextMemoryAddress; startingIndex < this.bufferTotalByteSize; startingIndex += this.chunkByteSize)
		{
			// check if this section is free
			BufferSlice newSlice = new BufferSlice(startingIndex, this.chunkByteSize);
			if (this.bufferSliceByStartingIndex.putIfAbsent(startingIndex, newSlice) == null)
			{
				this.nextMemoryAddress = startingIndex;
				return newSlice;
			}
		}
		
		return null; // No free chunk found
	}
	public void deallocate(BufferSlice[] slices)
	{
		if (slices != null)
		{
			for (BufferSlice slice : slices)
			{
				if (slice != null)
				{
					this.bufferSliceByStartingIndex.remove(slice.startIndex);
					this.availableSlices.add(slice);
				}
			}
		}
	}
	public void deallocate(BufferSlice slice) 
	{
		if (slice != null)
		{
			this.bufferSliceByStartingIndex.remove(slice.startIndex);
			this.availableSlices.add(slice);
		}
	}
	public void clear() 
	{
		this.bufferSliceByStartingIndex.clear(); 
	}
	
	
	
	//=================//
	// buffer handling //
	//=================//
	
	public void updateBuffer(BufferSlice chunk, ByteBuffer buffer) 
	{
		int size = buffer.limit() - buffer.position();
		if (size > chunk.length)
		{
			// if this was fired that means we didn't split up the buffer into the right size
			// if this isn't stopped the buffer will overwrite an adjacent section and cause incorrect rendering
			throw new RuntimeException("Programmer error: Uploaded buffer bigger than the allocated area. Allocated: ["+chunk.length+"], buffer: ["+size+"]");
		}
		
		GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.vboId);
		GL32.glBufferSubData(GL32.GL_ARRAY_BUFFER, chunk.startIndex, buffer);
		GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, 0);
		chunk.vertexCount = size / LodUtil.LOD_VERTEX_FORMAT.getByteSize();
	}
	
	public void bind() { GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, this.vboId); }
	public void unbind() { GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, 0); }
	
	
	
	//=======//
	// debug //
	//=======//
	
	public String getDebugMenuString()
	{
		long maxChunkCount = (this.bufferTotalByteSize / this.chunkByteSize);
		long chunkCount = 0;
		long allocatedBytes = 0;
		
		for (BufferSlice slice : this.bufferSliceByStartingIndex.values())
		{
			allocatedBytes += slice.length;
			chunkCount++;
		}
		
		return "Slices: ["+F3Screen.NUMBER_FORMAT.format(chunkCount)+"/"+F3Screen.NUMBER_FORMAT.format(maxChunkCount)+"], " +
				"Mem: ["+StringUtil.convertByteCountToHumanReadableSI(allocatedBytes)+"/"+StringUtil.convertByteCountToHumanReadableSI(this.bufferTotalByteSize)+"]";
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** represents a single allocated slice of the parent VBO */
	public static class BufferSlice 
	{
		public final long startIndex;
		public final int length;
		
		public int vertexCount;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public BufferSlice(long startIndex, int length) 
		{
			this.startIndex = startIndex;
			this.length = length;
		}
		
	}
	
}
