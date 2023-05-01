package com.seibel.lod.core.dataObjects.fullData.sources;

import com.seibel.lod.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.lod.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.lod.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.lod.core.dataObjects.fullData.accessor.FullDataArrayAccessor;
import com.seibel.lod.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.lod.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.lod.core.level.IDhLevel;
import com.seibel.lod.core.pos.DhLodPos;
import com.seibel.lod.core.pos.DhSectionPos;
import com.seibel.lod.core.logging.DhLoggerBuilder;
import com.seibel.lod.coreapi.util.BitShiftUtil;
import com.seibel.lod.core.util.FullDataPointUtil;
import com.seibel.lod.core.util.LodUtil;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.BitSet;

/**
 * Used for small incomplete LOD blocks.<br>
 * Handles incomplete full data with a detail level equal to or lower than 
 * {@link HighDetailIncompleteFullDataSource#MAX_SECTION_DETAIL}.
 * 
 * @see LowDetailIncompleteFullDataSource
 * @see CompleteFullDataSource
 * @see FullDataPointUtil
 */
public class HighDetailIncompleteFullDataSource implements IIncompleteFullDataSource
{
    private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
    public static final byte SPARSE_UNIT_DETAIL = LodUtil.CHUNK_DETAIL_LEVEL;
    public static final byte SPARSE_UNIT_SIZE = (byte) BitShiftUtil.powerOfTwo(SPARSE_UNIT_DETAIL);
	
    public static final byte SECTION_SIZE_OFFSET = 6;
    public static final int SECTION_SIZE = (byte) BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET);
	/** aka max detail level */
    public static final byte MAX_SECTION_DETAIL = SECTION_SIZE_OFFSET + SPARSE_UNIT_DETAIL;
    public static final byte LATEST_VERSION = 0;
    public static final long TYPE_ID = "HighDetailIncompleteFullDataSource".hashCode();
	
	/**
	 * This is the byte put between different sections in the binary save file.
	 * The presence and absence of this byte indicates if the file is correctly formatted.  
	 */
	private static final int DATA_GUARD_BYTE = 0xFFFFFFFF;
	/** indicates the binary save file represents an empty data source */
	private static final int NO_DATA_FLAG_BYTE = 0x00000001;
	
	protected final FullDataPointIdMap mapping;
    private final DhSectionPos sectionPos;
    private final FullDataArrayAccessor[] sparseData;
    private final DhLodPos chunkPos;
	
	public final int chunks;
	public final int dataPerChunk;
    public boolean isEmpty = true;
	
	
	
	//==============//
	// constructors //
	//==============//
	
    public static HighDetailIncompleteFullDataSource createEmpty(DhSectionPos pos) { return new HighDetailIncompleteFullDataSource(pos); }
    private HighDetailIncompleteFullDataSource(DhSectionPos sectionPos)
	{
        LodUtil.assertTrue(sectionPos.sectionDetailLevel > SPARSE_UNIT_DETAIL);
        LodUtil.assertTrue(sectionPos.sectionDetailLevel <= MAX_SECTION_DETAIL);
		
        this.sectionPos = sectionPos;
		this.chunks = 1 << (byte) (sectionPos.sectionDetailLevel - SPARSE_UNIT_DETAIL);
		this.dataPerChunk = SECTION_SIZE / this.chunks;
		
		this.sparseData = new FullDataArrayAccessor[this.chunks * this.chunks];
		this.chunkPos = sectionPos.getCorner(SPARSE_UNIT_DETAIL);
		this.mapping = new FullDataPointIdMap();
    }
	
    protected HighDetailIncompleteFullDataSource(DhSectionPos sectionPos, FullDataPointIdMap mapping, FullDataArrayAccessor[] data)
	{
        LodUtil.assertTrue(sectionPos.sectionDetailLevel > SPARSE_UNIT_DETAIL);
        LodUtil.assertTrue(sectionPos.sectionDetailLevel <= MAX_SECTION_DETAIL);
		
        this.sectionPos = sectionPos;
		this.chunks = 1 << (byte) (sectionPos.sectionDetailLevel - SPARSE_UNIT_DETAIL);
		this.dataPerChunk = SECTION_SIZE / this.chunks;
		
		LodUtil.assertTrue(this.chunks * this.chunks == data.length);
		this.sparseData = data;
		this.chunkPos = sectionPos.getCorner(SPARSE_UNIT_DETAIL);
		this.isEmpty = false;
        this.mapping = mapping;
    }
	
	
	
	//==============//
	// data getters // 
	//==============//
	
    @Override
    public DhSectionPos getSectionPos() { return this.sectionPos; }
    @Override
    public byte getDataDetailLevel() { return (byte) (this.sectionPos.sectionDetailLevel - SECTION_SIZE_OFFSET); }

    @Override
    public byte getDataVersion() { return LATEST_VERSION; }
	
	// TODO implement
	@Override 
	public EDhApiWorldGenerationStep getWorldGenStep() { return EDhApiWorldGenerationStep.EMPTY; }
	
	@Override
	public FullDataPointIdMap getMapping() { return this.mapping; }
	
    private int calculateOffset(int chunkX, int chunkZ)
	{
        int offsetX = chunkX - this.chunkPos.x;
        int offsetZ = chunkZ - this.chunkPos.z;
        LodUtil.assertTrue(offsetX >= 0 && offsetZ >= 0 && offsetX < this.chunks && offsetZ < this.chunks);
        return offsetX * this.chunks + offsetZ;
    }
	
	
    @Override
    public void update(ChunkSizedFullDataAccessor chunkDataView)
	{
		int arrayOffset = this.calculateOffset(chunkDataView.pos.x, chunkDataView.pos.z);
		FullDataArrayAccessor newArray = new FullDataArrayAccessor(this.mapping, new long[this.dataPerChunk * this.dataPerChunk][], this.dataPerChunk);
		if (this.getDataDetailLevel() == chunkDataView.detailLevel)
		{
			chunkDataView.shadowCopyTo(newArray);
		}
		else
		{
			int count = this.dataPerChunk;
			int dataPerCount = SPARSE_UNIT_SIZE / this.dataPerChunk;
	
			for (int xOffset = 0; xOffset < count; xOffset++)
			{
				for (int zOffset = 0; zOffset < count; zOffset++)
				{
					SingleColumnFullDataAccessor column = newArray.get(xOffset, zOffset);
					column.downsampleFrom(chunkDataView.subView(dataPerCount, xOffset * dataPerCount, zOffset * dataPerCount));
				}
			}
		}
		
		this.isEmpty = false;
		this.sparseData[arrayOffset] = newArray;
    }
	
    @Override
    public boolean isEmpty() { return this.isEmpty; }
	
	
    @Override
	public void sampleFrom(IFullDataSource fullDataSource)
	{
		DhSectionPos pos = fullDataSource.getSectionPos();
		LodUtil.assertTrue(pos.sectionDetailLevel < this.sectionPos.sectionDetailLevel);
		LodUtil.assertTrue(pos.overlaps(this.sectionPos));
		if (fullDataSource.isEmpty())
		{
			return;
		}
		
		
		if (fullDataSource instanceof HighDetailIncompleteFullDataSource)
		{
			this.sampleFrom((HighDetailIncompleteFullDataSource) fullDataSource);
		}
		else if (fullDataSource instanceof CompleteFullDataSource)
		{
			this.sampleFrom((CompleteFullDataSource) fullDataSource);
		}
		else
		{
			LodUtil.assertNotReach("SampleFrom not implemented for ["+IFullDataSource.class.getSimpleName()+"] with class ["+fullDataSource.getClass().getSimpleName()+"].");
		}
	}
	
    private void sampleFrom(HighDetailIncompleteFullDataSource sparseDataSource)
	{
        DhSectionPos pos = sparseDataSource.getSectionPos();
		this.isEmpty = false;
		
        DhLodPos basePos = this.sectionPos.getCorner(SPARSE_UNIT_DETAIL);
        DhLodPos dataPos = pos.getCorner(SPARSE_UNIT_DETAIL);
       
        int offsetX = dataPos.x-basePos.x;
        int offsetZ = dataPos.z-basePos.z;
		LodUtil.assertTrue(offsetX >= 0 && offsetX < this.chunks && offsetZ >= 0 && offsetZ < this.chunks);
	
		for (int xOffset = 0; xOffset < sparseDataSource.chunks; xOffset++)
		{
			for (int zOffset = 0; zOffset < sparseDataSource.chunks; zOffset++)
			{
				FullDataArrayAccessor sourceChunk = sparseDataSource.sparseData[xOffset * sparseDataSource.chunks + zOffset];
				if (sourceChunk != null)
				{
					FullDataArrayAccessor newFullDataAccessor = new FullDataArrayAccessor(this.mapping, new long[this.dataPerChunk * this.dataPerChunk][], this.dataPerChunk);
					newFullDataAccessor.downsampleFrom(sourceChunk);
					this.sparseData[(xOffset + offsetX) * this.chunks + (zOffset + offsetZ)] = newFullDataAccessor;
				}
			}
		}
    }
    private void sampleFrom(CompleteFullDataSource completeDataSource)
	{
        DhSectionPos pos = completeDataSource.getSectionPos();
		this.isEmpty = false;

        DhLodPos basePos = this.sectionPos.getCorner(SPARSE_UNIT_DETAIL);
        DhLodPos dataPos = pos.getCorner(SPARSE_UNIT_DETAIL);
        
		int coveredChunks = pos.getWidth(SPARSE_UNIT_DETAIL).numberOfLodSectionsWide;
        int sourceDataPerChunk = SPARSE_UNIT_SIZE >>> completeDataSource.getDataDetailLevel();
        LodUtil.assertTrue((coveredChunks * sourceDataPerChunk) == CompleteFullDataSource.SECTION_SIZE);
        
		int xDataOffset = dataPos.x - basePos.x;
        int zDataOffset = dataPos.z - basePos.z;
        LodUtil.assertTrue(xDataOffset >= 0 && xDataOffset < this.chunks && zDataOffset >= 0 && zDataOffset < this.chunks);
	
		for (int xOffset = 0; xOffset < coveredChunks; xOffset++)
		{
			for (int zOffset = 0; zOffset < coveredChunks; zOffset++)
			{
				FullDataArrayAccessor sourceChunk = completeDataSource.subView(sourceDataPerChunk, xOffset * sourceDataPerChunk, zOffset * sourceDataPerChunk);
				FullDataArrayAccessor newFullDataAccessor = new FullDataArrayAccessor(this.mapping, new long[this.dataPerChunk * this.dataPerChunk][], this.dataPerChunk);
				newFullDataAccessor.downsampleFrom(sourceChunk);
				this.sparseData[(xOffset + xDataOffset) * this.chunks + (zOffset + zDataOffset)] = newFullDataAccessor;
			}
		}
    }
	
	
	
	//===============//
	// file handling //
	//===============//
	
    @Override
    public void writeToStream(IDhLevel level, FullDataMetaFile file, BufferedOutputStream bufferedOutputStream) throws IOException
	{
        DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
		
			
		dataOutputStream.writeShort(this.getDataDetailLevel());
		dataOutputStream.writeShort(SPARSE_UNIT_DETAIL);
		dataOutputStream.writeInt(SECTION_SIZE);
		dataOutputStream.writeInt(level.getMinY());
		if (this.isEmpty)
		{
			dataOutputStream.writeInt(NO_DATA_FLAG_BYTE);
			return;
		}
		
		dataOutputStream.writeInt(DATA_GUARD_BYTE);
		// sparse array existence bitset
		BitSet dataArrayIndexHasData = new BitSet(this.sparseData.length);
		for (int i = 0; i < this.sparseData.length; i++)
		{
			dataArrayIndexHasData.set(i, this.sparseData[i] != null);
		}
		byte[] bytes = dataArrayIndexHasData.toByteArray();
		dataOutputStream.writeInt(bytes.length);
		dataOutputStream.write(bytes);

		// Data array content (only on non-empty stuff)
		dataOutputStream.writeInt(DATA_GUARD_BYTE);
		for (int i = dataArrayIndexHasData.nextSetBit(0); 
			 i >= 0; 
			 i = dataArrayIndexHasData.nextSetBit(i+1))
		{
			// column data length
			FullDataArrayAccessor array = this.sparseData[i];
			LodUtil.assertTrue(array != null);
			for (int x = 0; x < array.width(); x++)
			{
				for (int z = 0; z < array.width(); z++)
				{
					dataOutputStream.writeInt(array.get(x, z).getSingleLength());
				}
			}
			
			// column data
			for (int x = 0; x < array.width(); x++)
			{
				for (int z = 0; z < array.width(); z++)
				{
					SingleColumnFullDataAccessor column = array.get(x, z);
					LodUtil.assertTrue(column.getMapping() == this.mapping); // the mappings must be exactly equal!
					
					if (column.doesColumnExist())
					{
						long[] raw = column.getRaw();
						for (long l : raw)
						{
							dataOutputStream.writeLong(l);
						}
					}
				}
			}
		}
		
		// Id mapping
		dataOutputStream.writeInt(DATA_GUARD_BYTE);
		this.mapping.serialize(bufferedOutputStream);
		dataOutputStream.writeInt(DATA_GUARD_BYTE);
    }

    public static HighDetailIncompleteFullDataSource loadData(FullDataMetaFile dataFile, BufferedInputStream bufferedInputStream, IDhLevel level) throws IOException, InterruptedException
	{
        LodUtil.assertTrue(dataFile.pos.sectionDetailLevel > SPARSE_UNIT_DETAIL);
        LodUtil.assertTrue(dataFile.pos.sectionDetailLevel <= MAX_SECTION_DETAIL);
		
        DataInputStream dataInputStream = new DataInputStream(bufferedInputStream); // DO NOT CLOSE! It would close all related streams
        
		
		// TODO what is a data detail?
		int dataDetail = dataInputStream.readShort();
		if(dataDetail != dataFile.metaData.dataLevel)
		{
			throw new IOException(LodUtil.formatLog("Data level mismatch: {} != {}", dataDetail, dataFile.metaData.dataLevel));
		}
		
		// confirm that the detail level is correct
		int sparseDetail = dataInputStream.readShort();
		if (sparseDetail != SPARSE_UNIT_DETAIL)
		{
			throw new IOException((LodUtil.formatLog("Unexpected sparse detail level: {} != {}",
					sparseDetail, SPARSE_UNIT_DETAIL)));
		}
		
		// confirm the scale of the data points is correct
		int sectionSize = dataInputStream.readInt();
		if (sectionSize != SECTION_SIZE)
		{
			throw new IOException(LodUtil.formatLog(
					"Section size mismatch: {} != {} (Currently only 1 section size is supported)", sectionSize, SECTION_SIZE));
		}
		
		
		// calculate the number of chunks and dataPoints based on the sparseDetail and sectionSize
		// TODO these values should be constant, should we still be calculating them like this?
		int chunks = BitShiftUtil.powerOfTwo(dataFile.pos.sectionDetailLevel - sparseDetail);
		int dataPointsPerChunk = sectionSize / chunks;
		
		
		// get the data's starting Y-level
		int minY = dataInputStream.readInt();
		if (minY != level.getMinY())
		{
			LOGGER.warn("Data minY mismatch: {} != {}. Will ignore data's y level", minY, level.getMinY());
		}
		
		
		// check if this file has any data
		int hasDataFlag = dataInputStream.readInt();
		if (hasDataFlag == NO_DATA_FLAG_BYTE)
		{
			// this file is empty
			return createEmpty(dataFile.pos);
		}
		else if (hasDataFlag != DATA_GUARD_BYTE)
		{
			// the file format is incorrect
			throw new IOException("invalid header end guard");
		}
		else
		{
			// this file has data
			
			
			// get the number of columns (IE the bitSet from before)
			int numberOfDataColumns = dataInputStream.readInt();
			// validate the number of data columns
			int maxNumberOfDataColumns = (chunks * chunks / 8 + 64) * 2; // TODO what do these values represent?
			if (numberOfDataColumns < 0 || numberOfDataColumns > maxNumberOfDataColumns)
			{
				throw new IOException(LodUtil.formatLog("Sparse Flag BitSet size outside reasonable range: {} (expects {} to {})",
						numberOfDataColumns, 1, maxNumberOfDataColumns));
			}
			
			// read in the presence of each data column
			byte[] bytes = new byte[numberOfDataColumns];
			dataInputStream.readFully(bytes, 0, numberOfDataColumns);
			BitSet dataArrayIndexHasData = BitSet.valueOf(bytes);
			
			
			
			//====================//
			// Data array content //
			//====================//
			
			//  (only on non-empty columns)
			int dataArrayStartByte = dataInputStream.readInt();
			// confirm the column data is starting
			if (dataArrayStartByte != DATA_GUARD_BYTE)
			{
				// the file format is incorrect
				throw new IOException("invalid data length end guard");
			}
			
			
			// read in each column that has data written to it
			long[][][] rawFullDataArrays = new long[chunks * chunks][][];
			for (int fullDataIndex = dataArrayIndexHasData.nextSetBit(0);
				 fullDataIndex >= 0 && // TODO why does this happen? 
						 fullDataIndex < rawFullDataArrays.length;
				 fullDataIndex = dataArrayIndexHasData.nextSetBit(fullDataIndex + 1))
			{
				long[][] dataColumn = new long[dataPointsPerChunk * dataPointsPerChunk][];
				
				// get the column data lengths
				rawFullDataArrays[fullDataIndex] = dataColumn;
				for (int x = 0; x < dataColumn.length; x++)
				{
					// this should be zero if the column doesn't have any data
					int dataColumnLength = dataInputStream.readInt();
					dataColumn[x] = new long[dataColumnLength];
				}
				
				// get the column data
				for (int x = 0; x < dataColumn.length; x++)
				{
					if (dataColumn[x].length != 0)
					{
						// read in the data columns
						for (int z = 0; z < dataColumn[x].length; z++)
						{
							dataColumn[x][z] = dataInputStream.readLong();
						}
					}
				}
			}
			
			
			
			//============//
			// ID mapping //
			//============//
			
			// mark the start of the ID data
			int idMappingStartByte = dataInputStream.readInt();
			if (idMappingStartByte != DATA_GUARD_BYTE)
			{
				// the file format is incorrect
				throw new IOException("invalid data content end guard");
			}
			
			// deserialize the ID data
			FullDataPointIdMap mapping = FullDataPointIdMap.deserialize(bufferedInputStream);
			int idMappingEndByte = dataInputStream.readInt();
			if (idMappingEndByte != DATA_GUARD_BYTE)
			{
				// the file format is incorrect
				throw new IOException("invalid id mapping end guard");
			}
			
			FullDataArrayAccessor[] fullDataArrays = new FullDataArrayAccessor[chunks * chunks];
			for (int i = 0; i < rawFullDataArrays.length; i++)
			{
				if (rawFullDataArrays[i] != null)
				{
					fullDataArrays[i] = new FullDataArrayAccessor(mapping, rawFullDataArrays[i], dataPointsPerChunk);
				}
			}
			
			return new HighDetailIncompleteFullDataSource(dataFile.pos, mapping, fullDataArrays);
		}
    }
	
	
	
    private void applyToFullDataSource(CompleteFullDataSource dataSource)
	{
        LodUtil.assertTrue(dataSource.getSectionPos().equals(this.sectionPos));
        LodUtil.assertTrue(dataSource.getDataDetailLevel() == this.getDataDetailLevel());
		for (int x = 0; x < this.chunks; x++)
		{
			for (int z = 0; z < this.chunks; z++)
			{
				FullDataArrayAccessor array = this.sparseData[x * this.chunks + z];
				if (array == null)
					continue;
				
				// Otherwise, apply data to dataSource
				dataSource.markNotEmpty();
				FullDataArrayAccessor view = dataSource.subView(this.dataPerChunk, x * this.dataPerChunk, z * this.dataPerChunk);
				array.shadowCopyTo(view);
			}
		}
    }

    public IFullDataSource tryPromotingToCompleteDataSource()
	{
        if (this.isEmpty)
		{
			return this;
		}
		
		// promotion can only succeed if every data column is present
        for (FullDataArrayAccessor array : this.sparseData)
		{
			if (array == null)
			{
				return this;
			}
		}
		
        CompleteFullDataSource fullDataSource = CompleteFullDataSource.createEmpty(this.sectionPos);
		this.applyToFullDataSource(fullDataSource);
        return fullDataSource;
    }
	
    public SingleColumnFullDataAccessor tryGet(int relativeX, int relativeZ)
	{
        LodUtil.assertTrue(relativeX >=0 && relativeX <SECTION_SIZE && relativeZ >=0 && relativeZ <SECTION_SIZE);
        int chunkX = relativeX / this.dataPerChunk;
        int chunkZ = relativeZ / this.dataPerChunk;
        FullDataArrayAccessor chunk = this.sparseData[chunkX * this.chunks + chunkZ];
        if (chunk == null)
		{
			return null;
		}
		
        return chunk.get(relativeX % this.dataPerChunk, relativeZ % this.dataPerChunk);
    }
	
}
