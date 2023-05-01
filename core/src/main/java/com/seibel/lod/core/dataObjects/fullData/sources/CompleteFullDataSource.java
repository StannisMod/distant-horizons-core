package com.seibel.lod.core.dataObjects.fullData.sources;

import com.seibel.lod.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.lod.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.lod.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.lod.core.dataObjects.fullData.accessor.FullDataArrayAccessor;
import com.seibel.lod.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.lod.core.level.IDhLevel;
import com.seibel.lod.core.pos.DhBlockPos2D;
import com.seibel.lod.core.pos.DhLodPos;
import com.seibel.lod.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.lod.core.pos.DhSectionPos;
import com.seibel.lod.coreapi.util.BitShiftUtil;
import com.seibel.lod.core.logging.DhLoggerBuilder;
import com.seibel.lod.core.util.FullDataPointUtil;
import com.seibel.lod.core.util.LodUtil;
import org.apache.logging.log4j.Logger;

import java.io.*;

/**
 * This data source contains every datapoint over its given {@link DhSectionPos}.
 * 
 * @see FullDataPointUtil
 * @see LowDetailIncompleteFullDataSource
 * @see HighDetailIncompleteFullDataSource
 */
public class CompleteFullDataSource extends FullDataArrayAccessor implements IFullDataSource
{
    private static final Logger LOGGER = DhLoggerBuilder.getLogger();
    public static final byte SECTION_SIZE_OFFSET = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
    public static final int SECTION_SIZE = BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET);
    public static final byte LATEST_VERSION = 0;
    public static final long TYPE_ID = "CompleteFullDataSource".hashCode();
	
    private final DhSectionPos sectionPos;
    private boolean isEmpty = true;
	
	
	
	
	public static CompleteFullDataSource createEmpty(DhSectionPos pos) { return new CompleteFullDataSource(pos); }
	private CompleteFullDataSource(DhSectionPos sectionPos)
	{
        super(new FullDataPointIdMap(), new long[SECTION_SIZE*SECTION_SIZE][0], SECTION_SIZE);
        this.sectionPos = sectionPos;
    }
	
	public CompleteFullDataSource(DhSectionPos pos, FullDataPointIdMap mapping, long[][] data)
	{
		super(mapping, data, SECTION_SIZE);
		LodUtil.assertTrue(data.length == SECTION_SIZE * SECTION_SIZE);
		
		this.sectionPos = pos;
		this.isEmpty = false;
	}
	
	
	
    @Override
    public DhSectionPos getSectionPos() {  return this.sectionPos; }
    @Override
    public byte getDataDetailLevel() { return (byte) (this.sectionPos.sectionDetailLevel -SECTION_SIZE_OFFSET); }

    @Override
    public byte getDataVersion() { return LATEST_VERSION; }
	
	// TODO implement
	@Override
	public EDhApiWorldGenerationStep getWorldGenStep() { return EDhApiWorldGenerationStep.EMPTY; }
	
	@Override
	public SingleColumnFullDataAccessor tryGet(int relativeX, int relativeZ) { return this.get(relativeX, relativeZ); }
	
	@Override
	public void update(ChunkSizedFullDataAccessor chunkDataView)
	{
		LodUtil.assertTrue(this.sectionPos.getSectionBBoxPos().overlapsExactly(chunkDataView.getLodPos()));
		if (this.getDataDetailLevel() == LodUtil.BLOCK_DETAIL_LEVEL)
		{
			DhBlockPos2D chunkBlockPos = new DhBlockPos2D(chunkDataView.pos.x * LodUtil.CHUNK_WIDTH, chunkDataView.pos.z * LodUtil.CHUNK_WIDTH);
			DhBlockPos2D blockOffset = chunkBlockPos.subtract(this.sectionPos.getCorner().getCornerBlockPos());
			LodUtil.assertTrue(blockOffset.x >= 0 && blockOffset.x < SECTION_SIZE && blockOffset.z >= 0 && blockOffset.z < SECTION_SIZE);
			this.isEmpty = false;
			
			chunkDataView.shadowCopyTo(this.subView(LodUtil.CHUNK_WIDTH, blockOffset.x, blockOffset.z));
			
			// DEBUG ASSERTION
			{
				for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
				{
					for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
					{
						SingleColumnFullDataAccessor column = this.get(x + blockOffset.x, z + blockOffset.z);
						LodUtil.assertTrue(column.doesColumnExist());
					}
				}
			}
		}
		else if (this.getDataDetailLevel() < LodUtil.CHUNK_DETAIL_LEVEL)
		{
			int dataPerFull = 1 << this.getDataDetailLevel();
			int fullSize = LodUtil.CHUNK_WIDTH / dataPerFull;
			DhLodPos dataOffset = chunkDataView.getLodPos().getCornerLodPos(this.getDataDetailLevel());
			DhLodPos baseOffset = this.sectionPos.getCorner(this.getDataDetailLevel());
			
			int offsetX = dataOffset.x - baseOffset.x;
			int offsetZ = dataOffset.z - baseOffset.z;
			LodUtil.assertTrue(offsetX >= 0 && offsetX < SECTION_SIZE && offsetZ >= 0 && offsetZ < SECTION_SIZE);
			
			this.isEmpty = false;
			for (int xOffset = 0; xOffset < fullSize; xOffset++)
			{
				for (int zOffset = 0; zOffset < fullSize; zOffset++)
				{
					SingleColumnFullDataAccessor column = this.get(xOffset + offsetX, zOffset + offsetZ);
					column.downsampleFrom(chunkDataView.subView(dataPerFull, xOffset * dataPerFull, zOffset * dataPerFull));
				}
			}
		}
		else if (this.getDataDetailLevel() >= LodUtil.CHUNK_DETAIL_LEVEL)
		{
			//FIXME: TEMPORARY
			int chunkPerFull = 1 << (this.getDataDetailLevel() - LodUtil.CHUNK_DETAIL_LEVEL);
			if (chunkDataView.pos.x % chunkPerFull != 0 || chunkDataView.pos.z % chunkPerFull != 0)
			{
				return;
			}
			
			DhLodPos baseOffset = this.sectionPos.getCorner(this.getDataDetailLevel());
			DhLodPos dataOffset = chunkDataView.getLodPos().convertToDetailLevel(this.getDataDetailLevel());
			
			int offsetX = dataOffset.x - baseOffset.x;
			int offsetZ = dataOffset.z - baseOffset.z;
			LodUtil.assertTrue(offsetX >= 0 && offsetX < SECTION_SIZE && offsetZ >= 0 && offsetZ < SECTION_SIZE);
			
			this.isEmpty = false;
			chunkDataView.get(0, 0).deepCopyTo(this.get(offsetX, offsetZ));
		}
		else
		{
			LodUtil.assertNotReach();
			//TODO
		}
		
	}

    @Override
    public boolean isEmpty() { return this.isEmpty; }
    public void markNotEmpty() { this.isEmpty = false; }
	
	
	
	public static CompleteFullDataSource loadData(FullDataMetaFile dataFile, BufferedInputStream bufferedInputStream, IDhLevel level) throws IOException, InterruptedException
	{
		DataInputStream dataInputStream = new DataInputStream(bufferedInputStream); // DO NOT CLOSE
		
		int dataDetail = dataInputStream.readInt();
		if (dataDetail != dataFile.metaData.dataLevel)
		{
			throw new IOException(LodUtil.formatLog("Data level mismatch: {} != {}", dataDetail, dataFile.metaData.dataLevel));
		}
		
		int size = dataInputStream.readInt();
		if (size != SECTION_SIZE)
		{
			throw new IOException(LodUtil.formatLog(
					"Section size mismatch: {} != {} (Currently only 1 section size is supported)", size, SECTION_SIZE));
		}
		
		int minY = dataInputStream.readInt();
		if (minY != level.getMinY())
		{
			LOGGER.warn("Data minY mismatch: {} != {}. Will ignore data's y level", minY, level.getMinY());
		}
		int end = dataInputStream.readInt();
		
		// Data array length
		if (end == IFullDataSource.NO_DATA_FLAG_BYTE)
		{
			// Section is empty
			return new CompleteFullDataSource(dataFile.pos);
		}
		// Non-empty section
		if (end != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("invalid header end guard");
		}
		
		long[][] data = new long[size * size][];
		for (int x = 0; x < size; x++)
		{
			for (int z = 0; z < size; z++)
			{
				data[x * size + z] = new long[dataInputStream.readInt()];
			}
		}
		// Data array content (only on non-empty columns)
		end = dataInputStream.readInt();
		if (end != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("invalid data length end guard");
		}
		for (int i = 0; i < data.length; i++)
		{
			if (data[i].length != 0)
			{
				for (int j = 0; j < data[i].length; j++)
				{
					data[i][j] = dataInputStream.readLong();
				}
			}
		}
		
		// Id mapping
		end = dataInputStream.readInt();
		if (end != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("invalid data content end guard");
		}
		
		FullDataPointIdMap mapping = FullDataPointIdMap.deserialize(bufferedInputStream);
		end = dataInputStream.readInt();
		if (end != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("invalid id mapping end guard");
		}
		
		return new CompleteFullDataSource(dataFile.pos, mapping, data);
	}
	
	@Override
	public void writeToStream(IDhLevel level, FullDataMetaFile file, BufferedOutputStream bufferedOutputStream) throws IOException
	{
		DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream); // DO NOT CLOSE
		
		
		dataOutputStream.writeInt(this.getDataDetailLevel());
		dataOutputStream.writeInt(this.width);
		dataOutputStream.writeInt(level.getMinY());
		if (this.isEmpty)
		{
			dataOutputStream.writeInt(0x00000001);
			return;
		}
		dataOutputStream.writeInt(0xFFFFFFFF);
		
		// Data array length
		for (int x = 0; x < this.width; x++)
		{
			for (int z = 0; z < this.width; z++)
			{
				dataOutputStream.writeInt(this.get(x, z).getSingleLength());
			}
		}
		
		// Data array content (only on non-empty columns)
		dataOutputStream.writeInt(0xFFFFFFFF);
		for (int x = 0; x < this.width; x++)
		{
			for (int z = 0; z < this.width; z++)
			{
				SingleColumnFullDataAccessor column = this.get(x, z);
				if (!column.doesColumnExist())
					continue;
				
				long[] raw = column.getRaw();
				for (long l : raw)
				{
					dataOutputStream.writeLong(l);
				}
			}
		}
		
		// Id mapping
		dataOutputStream.writeInt(0xFFFFFFFF);
		this.mapping.serialize(bufferedOutputStream);
		dataOutputStream.writeInt(0xFFFFFFFF);
	}
	
	
	/** Returns whether data at the given posToWrite can effect the target region file at posToTest. */
	public static boolean firstDataPosCanAffectSecond(DhSectionPos posToWrite, DhSectionPos posToTest)
	{
		if (!posToWrite.overlaps(posToTest))
		{
			// the testPosition is outside the writePosition
			return false;
		}
		else if (posToTest.sectionDetailLevel > posToWrite.sectionDetailLevel)
		{
			// the testPosition is larger (aka is less detailed) than the writePosition,
			// more detailed sections shouldn't be updated by lower detail sections
			return false;
		}
		else if (posToWrite.sectionDetailLevel - posToTest.sectionDetailLevel <= SECTION_SIZE_OFFSET)
		{
			// if the difference in detail levels is very large, the posToWrite
			// may be skipped, due to how we sample large detail levels by only
			// getting the corners.
			 
			// In this case the difference isn't very large, so return true
			return true;
		}
		else
		{
			// the difference in detail levels is very large,
			// check if the posToWrite is in a corner of posToTest
			byte sectPerData = (byte) BitShiftUtil.powerOfTwo(posToWrite.sectionDetailLevel - posToTest.sectionDetailLevel - SECTION_SIZE_OFFSET);
			return posToTest.sectionX % sectPerData == 0 && posToTest.sectionZ % sectPerData == 0;	
		}
	}
	
	public void writeFromLower(CompleteFullDataSource subData)
	{
		LodUtil.assertTrue(this.sectionPos.overlaps(subData.sectionPos));
		LodUtil.assertTrue(subData.sectionPos.sectionDetailLevel < this.sectionPos.sectionDetailLevel);
		if (!firstDataPosCanAffectSecond(this.sectionPos, subData.sectionPos))
		{
			return;
		}
		
		DhSectionPos lowerSectPos = subData.sectionPos;
		byte detailDiff = (byte) (this.sectionPos.sectionDetailLevel - subData.sectionPos.sectionDetailLevel);
		byte targetDataDetail = this.getDataDetailLevel();
		DhLodPos minDataPos = this.sectionPos.getCorner(targetDataDetail);
		if (detailDiff <= SECTION_SIZE_OFFSET)
		{
			int count = 1 << detailDiff;
			int dataPerCount = SECTION_SIZE / count;
			DhLodPos subDataPos = lowerSectPos.getSectionBBoxPos().getCornerLodPos(targetDataDetail);
			int dataOffsetX = subDataPos.x - minDataPos.x;
			int dataOffsetZ = subDataPos.z - minDataPos.z;
			LodUtil.assertTrue(dataOffsetX >= 0 && dataOffsetX < SECTION_SIZE && dataOffsetZ >= 0 && dataOffsetZ < SECTION_SIZE);
			
			for (int xOffset = 0; xOffset < count; xOffset++)
			{
				for (int zOffset = 0; zOffset < count; zOffset++)
				{
					SingleColumnFullDataAccessor column = this.get(xOffset + dataOffsetX, zOffset + dataOffsetZ);
					column.downsampleFrom(subData.subView(dataPerCount, xOffset * dataPerCount, zOffset * dataPerCount));
				}
			}
		}
		else
		{
			// Count == 1
			DhLodPos subDataPos = lowerSectPos.getSectionBBoxPos().convertToDetailLevel(targetDataDetail);
			int dataOffsetX = subDataPos.x - minDataPos.x;
			int dataOffsetZ = subDataPos.z - minDataPos.z;
			LodUtil.assertTrue(dataOffsetX >= 0 && dataOffsetX < SECTION_SIZE && dataOffsetZ >= 0 && dataOffsetZ < SECTION_SIZE);
			subData.get(0, 0).deepCopyTo(get(dataOffsetX, dataOffsetZ));
		}
	}
	
}
