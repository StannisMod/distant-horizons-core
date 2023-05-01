package com.seibel.lod.core.dataObjects.render;

import com.seibel.lod.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.lod.coreapi.ModInfo;
import com.seibel.lod.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.lod.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.lod.core.dataObjects.render.columnViews.ColumnQuadView;
import com.seibel.lod.core.dataObjects.render.columnViews.IColumnDataView;
import com.seibel.lod.core.dataObjects.render.bufferBuilding.ColumnRenderBuffer;
import com.seibel.lod.core.dataObjects.transformers.FullToColumnTransformer;
import com.seibel.lod.core.level.IDhClientLevel;
import com.seibel.lod.core.pos.DhSectionPos;
import com.seibel.lod.core.enums.ELodDirection;
import com.seibel.lod.core.logging.DhLoggerBuilder;
import com.seibel.lod.core.level.IDhLevel;
import com.seibel.lod.coreapi.util.BitShiftUtil;
import com.seibel.lod.core.util.ColorUtil;
import com.seibel.lod.core.util.RenderDataPointUtil;
import com.seibel.lod.core.util.objects.Reference;
import com.seibel.lod.core.util.LodUtil;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores the render data used to generate OpenGL buffers.
 *
 * @see	RenderDataPointUtil
 */
public class ColumnRenderSource
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final boolean DO_SAFETY_CHECKS = ModInfo.IS_DEV_BUILD;
	public static final byte SECTION_SIZE_OFFSET = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	public static final int SECTION_SIZE = BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET);
	
	public static final byte DATA_FORMAT_VERSION = 1;
	public static final long TYPE_ID = "ColumnRenderSource".hashCode();
	
	/**
	 * This is the byte put between different sections in the binary save file.
	 * The presence and absence of this byte indicates if the file is correctly formatted.  
	 */
	public static final int DATA_GUARD_BYTE = 0xFFFFFFFF;
	/** indicates the binary save file represents an empty data source */
	public static final int NO_DATA_FLAG_BYTE = 0x00000001;
	
	
	
	public int verticalDataCount;
	public final DhSectionPos sectionPos;
	public final int yOffset;
	
	public long[] renderDataContainer;
	
	public final DebugSourceFlag[] debugSourceFlags;
	
	private boolean isEmpty = true;
	public EDhApiWorldGenerationStep worldGenStep;
	
	private IDhClientLevel level = null; //FIXME: hack to pass level into tryBuildBuffer
	
	//FIXME: Temp Hack to prevent swapping buffers too quickly
	private long lastNs = -1;
	/** 10 sec */
	private static final long SWAP_TIMEOUT_IN_NS = 10_000000000L;
	/** 1 sec */
	private static final long SWAP_BUSY_COLLISION_TIMEOUT_IN_NS = 1_000000000L;
	
	private CompletableFuture<ColumnRenderBuffer> buildRenderBufferFuture = null;
	private final Reference<ColumnRenderBuffer> columnRenderBufferRef = new Reference<>();
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static ColumnRenderSource createEmptyRenderSource(DhSectionPos sectionPos) { return new ColumnRenderSource(sectionPos, 0, 0); }
	/**
	 * Creates an empty ColumnRenderSource.
	 * 
	 * @param sectionPos the relative position of the container
	 * @param maxVerticalSize the maximum vertical size of the container
	 */
	public ColumnRenderSource(DhSectionPos sectionPos, int maxVerticalSize, int yOffset)
	{
		this.verticalDataCount = maxVerticalSize;
		this.renderDataContainer = new long[SECTION_SIZE * SECTION_SIZE * this.verticalDataCount];
		this.debugSourceFlags = new DebugSourceFlag[SECTION_SIZE * SECTION_SIZE];
		this.sectionPos = sectionPos;
		this.yOffset = yOffset;
		this.worldGenStep = EDhApiWorldGenerationStep.EMPTY;
	}
	
	/**
	 * Creates a new ColumnRenderSource from the parsedColumnData.
	 * 
	 * @throws IOException if the DataInputStream's detail level isn't what was expected
	 */
	public ColumnRenderSource(DhSectionPos sectionPos, ColumnRenderLoader.ParsedColumnData parsedColumnData, IDhLevel level) throws IOException
	{
		if (sectionPos.sectionDetailLevel - SECTION_SIZE_OFFSET != parsedColumnData.detailLevel)
		{
			throw new IOException("Invalid data: detail level does not match");
		}
		
		this.sectionPos = sectionPos;
		this.yOffset = level.getMinY();
		this.verticalDataCount = parsedColumnData.verticalSize;
		this.renderDataContainer = parsedColumnData.dataContainer;
		this.worldGenStep = parsedColumnData.worldGenStep;
		
		this.debugSourceFlags = new DebugSourceFlag[SECTION_SIZE * SECTION_SIZE];
		this.fillDebugFlag(0, 0, SECTION_SIZE, SECTION_SIZE, DebugSourceFlag.FILE);
	}
	
	
	
	//========================//
	// datapoint manipulation //
	//========================//
	
	public void clearDataPoint(int posX, int posZ)
	{
		for (int verticalIndex = 0; verticalIndex < this.verticalDataCount; verticalIndex++)
		{
			this.renderDataContainer[posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex] = RenderDataPointUtil.EMPTY_DATA;
		}
	}
	
	public boolean setDataPoint(long data, int posX, int posZ, int verticalIndex)
	{
		this.renderDataContainer[posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex] = data;
		return true;
	}
	
	public boolean copyVerticalData(IColumnDataView newData, int posX, int posZ, boolean overwriteDataWithSameGenerationMode)
	{
		if (DO_SAFETY_CHECKS)
		{
			if (newData.size() != this.verticalDataCount)
				throw new IllegalArgumentException("newData size not the same as this column's vertical size");
			if (posX < 0 || posX >= SECTION_SIZE)
				throw new IllegalArgumentException("X position is out of bounds");
			if (posZ < 0 || posZ >= SECTION_SIZE)
				throw new IllegalArgumentException("Z position is out of bounds");
		}
		
		int dataOffset = posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount;
		int compare = RenderDataPointUtil.compareDatapointPriority(newData.get(0), this.renderDataContainer[dataOffset]);
		if (overwriteDataWithSameGenerationMode)
		{
			if (compare < 0)
			{
				return false;
			}
		}
		else
		{
			if (compare <= 0)
			{
				return false;
			}
		}
		
		// copy the newData into this column's data
		newData.copyTo(this.renderDataContainer, dataOffset, newData.size());
		return true;
	}
	
	
	public long getFirstDataPoint(int posX, int posZ) { return getDataPoint(posX, posZ, 0); }
	public long getDataPoint(int posX, int posZ, int verticalIndex) { return this.renderDataContainer[posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex]; }
	
	public long[] getVerticalDataPointArray(int posX, int posZ)
	{
		long[] result = new long[this.verticalDataCount];
		int index = posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount;
		System.arraycopy(this.renderDataContainer, index, result, 0, this.verticalDataCount);
		return result;
	}
	
	public ColumnArrayView getVerticalDataPointView(int posX, int posZ)
	{
		return new ColumnArrayView(this.renderDataContainer, this.verticalDataCount,
				posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount,
				this.verticalDataCount);
	}
	
	public ColumnQuadView getFullQuadView() { return this.getQuadViewOverRange(0, 0, SECTION_SIZE, SECTION_SIZE); }
	public ColumnQuadView getQuadViewOverRange(int quadX, int quadZ, int quadXSize, int quadZSize) { return new ColumnQuadView(this.renderDataContainer, SECTION_SIZE, this.verticalDataCount, quadX, quadZ, quadXSize, quadZSize); }
	
	public int getVerticalSize() { return this.verticalDataCount; }
	
	
	
	//========================//
	// data update and output //
	//========================//
	
	public void writeData(BufferedOutputStream bufferedOutputStream) throws IOException
	{
		bufferedOutputStream.flush();
		DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
		
		dataOutputStream.writeByte(this.getDataDetail());
		dataOutputStream.writeInt(this.verticalDataCount);
		
		if (this.isEmpty)
		{
			// no data is present
			dataOutputStream.writeByte(NO_DATA_FLAG_BYTE);
		}
		else
		{
			// data is present
			dataOutputStream.writeByte(DATA_GUARD_BYTE);
			dataOutputStream.writeInt(this.yOffset);
			
			// write the data for each column
			for (int xz = 0; xz < SECTION_SIZE * SECTION_SIZE; xz++)
			{
				for (int y = 0; y < this.verticalDataCount; y++)
				{
					long currentDatapoint = this.renderDataContainer[xz * this.verticalDataCount + y];
					dataOutputStream.writeLong(Long.reverseBytes(currentDatapoint)); // the reverse bytes is necessary to ensure the data is read in correctly
				}
			}
		}
		
		dataOutputStream.writeByte(DATA_GUARD_BYTE);
		dataOutputStream.writeByte(this.worldGenStep.value);
		
		bufferedOutputStream.flush();
	}
	
	/** Overrides any data that has not been written directly using write(). Skips empty source dataPoints. */
	public void updateFromRenderSource(ColumnRenderSource renderSource)
	{
		
		// validate we are writing for the same location
		LodUtil.assertTrue(renderSource.sectionPos.equals(this.sectionPos));
		
		// change the vertical size if necessary (this can happen if the vertical quality was changed in the config) 
		this.clearAndChangeVerticalSize(renderSource.verticalDataCount);
		// validate both objects have the same number of dataPoints
		LodUtil.assertTrue(renderSource.verticalDataCount == this.verticalDataCount);
		
		
		if (renderSource.isEmpty)
		{
			// the source is empty, don't attempt to update anything
			return;
		}
		// the source isn't empty, this object won't be empty after the method finishes
		this.isEmpty = false;
		
		
		for (int i = 0; i < this.renderDataContainer.length; i += this.verticalDataCount)
		{
			int thisGenMode = RenderDataPointUtil.getGenerationMode(this.renderDataContainer[i]);
			int srcGenMode = RenderDataPointUtil.getGenerationMode(renderSource.renderDataContainer[i]);
			
			if (srcGenMode == 0)
			{
				// the source hasn't been generated, don't write it
				continue;
			}
			
			// this object's column is older than the source's column, update it
			if (thisGenMode <= srcGenMode)
			{
				ColumnArrayView thisColumnArrayView = new ColumnArrayView(this.renderDataContainer, this.verticalDataCount, i, this.verticalDataCount);
				ColumnArrayView srcColumnArrayView = new ColumnArrayView(renderSource.renderDataContainer, renderSource.verticalDataCount, i, renderSource.verticalDataCount);
				thisColumnArrayView.copyFrom(srcColumnArrayView);
				
				this.debugSourceFlags[i / this.verticalDataCount] = renderSource.debugSourceFlags[i / this.verticalDataCount];
			}
		}
	}
	/** 
	 * If the newVerticalSize is different than the current verticalSize,
	 * this will delete any data currently in this object and re-size it. <Br>
	 * Otherwise this method will do nothing.
	 */
	private void clearAndChangeVerticalSize(int newVerticalSize)
	{
		if (newVerticalSize != this.verticalDataCount)
		{
			this.verticalDataCount = newVerticalSize;
			this.renderDataContainer = new long[SECTION_SIZE * SECTION_SIZE * this.verticalDataCount];
		}
	}
	
	public void fastWrite(ChunkSizedFullDataAccessor chunkData, IDhClientLevel level) 
	{
		try
		{
			FullToColumnTransformer.writeFullDataChunkToColumnData(this, level, chunkData);
		}
		catch (InterruptedException e)
		{
			// expected if the transformer is shut down, the exception can be ignored
//			LOGGER.warn(ColumnRenderSource.class.getSimpleName()+" fast write interrupted.");
		}
	}
	
	
	
	//=====================//
	// data helper methods //
	//=====================//
	
	public boolean doesDataPointExist(int posX, int posZ) { return RenderDataPointUtil.doesDataPointExist(this.getFirstDataPoint(posX, posZ)); }
	
	public void generateData(ColumnRenderSource lowerDataContainer, int posX, int posZ)
	{
		ColumnArrayView outputView = this.getVerticalDataPointView(posX, posZ);
		ColumnQuadView quadView = lowerDataContainer.getQuadViewOverRange(posX * 2, posZ * 2, 2, 2);
		outputView.mergeMultiDataFrom(quadView);
	}
	
	public int getMaxLodCount() { return SECTION_SIZE * SECTION_SIZE * this.getVerticalSize(); }
	
	public long getRoughRamUsageInBytes() { return (long) this.renderDataContainer.length * Long.BYTES; }
	
	public DhSectionPos getSectionPos() { return this.sectionPos; }
	
	public byte getDataDetail() { return (byte) (this.sectionPos.sectionDetailLevel - SECTION_SIZE_OFFSET); }
	
	public int getDataSize() { return BitShiftUtil.powerOfTwo(this.getDetailOffset()); }
	public byte getDetailOffset() { return SECTION_SIZE_OFFSET; }
	
	
	
	//================//
	// Render Methods //
	//================//
	
	// TODO return future?
	private void tryBuildBuffer(IDhClientLevel level, ColumnRenderSource renderSource)
	{
		if (this.buildRenderBufferFuture == null && !ColumnRenderBuffer.isBusy() && !this.isEmpty)
		{
			ColumnRenderSource[] columnRenderSources = new ColumnRenderSource[ELodDirection.ADJ_DIRECTIONS.length];
			for (ELodDirection direction : ELodDirection.ADJ_DIRECTIONS)
			{
				if (renderSource != null)
				{
					columnRenderSources[direction.ordinal() - 2] = renderSource;
					//LOGGER.info("attempting to build buffer for: "+renderSection.pos);
				}
			}
			
			// TODO this needs to allow generating partial sections to allow for non-full quadTree RenderSections to render
			this.buildRenderBufferFuture = ColumnRenderBuffer.buildBuffers(level, this.columnRenderBufferRef, this, columnRenderSources);
		}
	}
	
	private void cancelBuildBuffer()
	{
		if (this.buildRenderBufferFuture != null)
		{
			//LOGGER.info("Cancelling build of render buffer for {}", sectionPos);
			this.buildRenderBufferFuture.cancel(true);
			this.buildRenderBufferFuture = null;
		}
	}
	
	public void allowRendering(IDhClientLevel level) { this.level = level; }
	
	public void disableRender() { this.cancelBuildBuffer(); }
	
	public void dispose() { this.cancelBuildBuffer(); }
	
	/**
	 * Try and swap in new render buffer for this section. Note that before this call, there should be no other
	 * places storing or referencing the render buffer.
	 * @param renderBufferRefToSwap The slot for swapping in the new buffer.
	 * @return True if the swap was successful. False if swap is not needed or if it is in progress.
	 */
	public boolean trySwapInNewlyBuiltRenderBuffer(ColumnRenderSource renderSource, AtomicReference<ColumnRenderBuffer> renderBufferRefToSwap)
	{
		// prevent swapping the buffer to quickly
		if (this.lastNs != -1 && System.nanoTime() - this.lastNs < SWAP_TIMEOUT_IN_NS)
		{
			return false;
		}
		
		
		if (this.buildRenderBufferFuture != null)
		{
			if (this.buildRenderBufferFuture.isDone())
			{
				this.lastNs = System.nanoTime();
				//LOGGER.info("Swapping render buffer for {}", sectionPos);
				
				
				ColumnRenderBuffer newBuffer = this.buildRenderBufferFuture.join();
				LodUtil.assertTrue(newBuffer.areBuffersUploaded(), "The buffer future for "+renderSource.sectionPos+" returned an un-built buffer.");
				
				ColumnRenderBuffer oldBuffer = renderBufferRefToSwap.getAndSet(newBuffer);
				if (oldBuffer != null)
				{
					// the old buffer is now considered unloaded, it will need to be freshly re-loaded
					oldBuffer.setBuffersUploaded(false);
				}
				
				ColumnRenderBuffer swapped = this.columnRenderBufferRef.swap(oldBuffer);
				LodUtil.assertTrue(swapped == null);
				
					
				this.buildRenderBufferFuture = null;
				return true;
			}
		}
		else
		{
			if (!this.isEmpty)
			{
				if (ColumnRenderBuffer.isBusy())
				{
					this.lastNs += (long) (SWAP_BUSY_COLLISION_TIMEOUT_IN_NS * Math.random());
				}
				else
				{
					this.tryBuildBuffer(this.level, renderSource);
				}
			}
		}
		
		return false;
	}
	
	public byte getRenderDataFormatVersion() { return DATA_FORMAT_VERSION; }
	
	/** 
	 * Whether this object is still valid. If not, a new one should be created.
	 * TODO this will be necessary for dedicated multiplayer support, if the server has newer data this section should no longer be valid
	 */
	public boolean isValid() { return true; }
	
	public boolean isEmpty() { return this.isEmpty; }
	public void markNotEmpty() { this.isEmpty = false; }
	
	
	
	//=======//
	// debug //
	//=======//
	
	/** Sets the debug flag for the given area */
	public void fillDebugFlag(int startX, int startZ, int width, int height, DebugSourceFlag flag)
	{
		for (int x = startX; x < startX + width; x++)
		{
			for (int z = startZ; z < startZ + height; z++)
			{
				this.debugSourceFlags[x * SECTION_SIZE + z] = flag;
			}
		}
	}
	
	public DebugSourceFlag debugGetFlag(int ox, int oz) { return this.debugSourceFlags[ox * SECTION_SIZE + oz]; }
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public String toString()
	{
		String LINE_DELIMITER = "\n";
		String DATA_DELIMITER = " ";
		String SUBDATA_DELIMITER = ",";
		StringBuilder stringBuilder = new StringBuilder();
		
		stringBuilder.append(this.sectionPos);
		stringBuilder.append(LINE_DELIMITER);
		
		int size = this.sectionPos.getWidth().numberOfLodSectionsWide;
		for (int z = 0; z < size; z++)
		{
			for (int x = 0; x < size; x++)
			{
				for (int y = 0; y < this.verticalDataCount; y++)
				{
					//Converting the dataToHex
					stringBuilder.append(Long.toHexString(this.getDataPoint(x, z, y)));
					if (y != this.verticalDataCount - 1)
						stringBuilder.append(SUBDATA_DELIMITER);
				}
				
				if (x != size - 1)
					stringBuilder.append(DATA_DELIMITER);
			}
			
			if (z != size - 1)
				stringBuilder.append(LINE_DELIMITER);
		}
		return stringBuilder.toString();
	}
	
	
	
	//==============//
	// helper enums //
	//==============//
	
	public enum DebugSourceFlag
	{
		FULL(ColorUtil.BLUE),
		DIRECT(ColorUtil.WHITE),
		SPARSE(ColorUtil.YELLOW),
		FILE(ColorUtil.BROWN);
		
		public final int color;
		
		DebugSourceFlag(int color) { this.color = color; }
	}
	
}
