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

package tests;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.io.File;

public class FullDataChecksumStabilityTest
{
	public static String DB_FILE_NAME_PREFIX = "DistantHorizonsTest-";
	public static String DB_FILE_NAME_SUFFIX = ".sqlite";
	
	
	
	//=================//
	// testing methods //
	//=================//
	
	@Test
	public void testIdenticalDataSources()
	{
		System.out.println("--- FullDataChecksumStabilityTest - Identical data sources ---");
		
		try
		{
			int checksum1, checksum2;
			
			{
				File uncompressedDatabaseFile = File.createTempFile(DB_FILE_NAME_PREFIX, DB_FILE_NAME_SUFFIX);
				System.out.println("First database location: " + uncompressedDatabaseFile.getAbsolutePath());
				Assert.assertTrue(uncompressedDatabaseFile.exists());
				
				try (FullDataSourceV2Repo uncompressedRepo = new FullDataSourceV2Repo("jdbc:sqlite", uncompressedDatabaseFile))
				{
					FullDataSourceV2 uncompressedDataSource = FullDataSourceV2.createEmpty(0);
					
					int mappingEntryId1 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("1"), new TestBlockStateWrapper());
					int mappingEntryId2 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("2"), new TestBlockStateWrapper());
					
					uncompressedDataSource.setSingleColumn(
							new LongArrayList(new long[]{
									FullDataPointUtil.encode(mappingEntryId1, 1, 0, (byte) 15, (byte) 15),
									FullDataPointUtil.encode(mappingEntryId2, 1, 1, (byte) 15, (byte) 15),
							}),
							0, 0,
							EDhApiWorldGenerationStep.FEATURES,
							EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS
					);
					
					uncompressedRepo.save(FullDataSourceV2DTO.CreateFromDataSource(uncompressedDataSource, EDhApiDataCompressionMode.UNCOMPRESSED));
					
					checksum1 = uncompressedRepo.getByKey(0L).dataChecksum;
				}
				
			}
			
			{
				File uncompressedDatabaseFile = File.createTempFile(DB_FILE_NAME_PREFIX, DB_FILE_NAME_SUFFIX);
				System.out.println("Second database location: " + uncompressedDatabaseFile.getAbsolutePath());
				Assert.assertTrue(uncompressedDatabaseFile.exists());
				
				try (FullDataSourceV2Repo uncompressedRepo = new FullDataSourceV2Repo("jdbc:sqlite", uncompressedDatabaseFile))
				{
					FullDataSourceV2 uncompressedDataSource = FullDataSourceV2.createEmpty(0);
					
					int mappingEntryId1 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("1"), new TestBlockStateWrapper());
					int mappingEntryId2 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("2"), new TestBlockStateWrapper());
					
					uncompressedDataSource.setSingleColumn(
							new LongArrayList(new long[]{
									FullDataPointUtil.encode(mappingEntryId1, 1, 0, (byte) 15, (byte) 15),
									FullDataPointUtil.encode(mappingEntryId2, 1, 1, (byte) 15, (byte) 15),
							}),
							0, 0,
							EDhApiWorldGenerationStep.FEATURES,
							EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS
					);
					
					uncompressedRepo.save(FullDataSourceV2DTO.CreateFromDataSource(uncompressedDataSource, EDhApiDataCompressionMode.UNCOMPRESSED));
					
					checksum2 = uncompressedRepo.getByKey(0L).dataChecksum;
				}
			}
			
			Assert.assertEquals(checksum1, checksum2);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
	@Test
	public void testDifferentMappingOrder()
	{
		System.out.println("--- FullDataChecksumStabilityTest - Different mapping order ---");
		
		try
		{
			int checksum1, checksum2;
			
			{
				File uncompressedDatabaseFile = File.createTempFile(DB_FILE_NAME_PREFIX, DB_FILE_NAME_SUFFIX);
				System.out.println("First database location: " + uncompressedDatabaseFile.getAbsolutePath());
				Assert.assertTrue(uncompressedDatabaseFile.exists());
				
				try (FullDataSourceV2Repo uncompressedRepo = new FullDataSourceV2Repo("jdbc:sqlite", uncompressedDatabaseFile))
				{
					FullDataSourceV2 uncompressedDataSource = FullDataSourceV2.createEmpty(0);
					
					int mappingEntryId1 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("2"), new TestBlockStateWrapper());
					int mappingEntryId2 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("1"), new TestBlockStateWrapper());
					
					uncompressedDataSource.setSingleColumn(
							new LongArrayList(new long[]{
									FullDataPointUtil.encode(mappingEntryId1, 1, 0, (byte) 15, (byte) 15),
									FullDataPointUtil.encode(mappingEntryId2, 1, 1, (byte) 15, (byte) 15),
							}),
							0, 0,
							EDhApiWorldGenerationStep.FEATURES,
							EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS
					);
					
					uncompressedRepo.save(FullDataSourceV2DTO.CreateFromDataSource(uncompressedDataSource, EDhApiDataCompressionMode.UNCOMPRESSED));
					
					checksum1 = uncompressedRepo.getByKey(0L).dataChecksum;
				}
				
			}
			
			{
				File uncompressedDatabaseFile = File.createTempFile(DB_FILE_NAME_PREFIX, DB_FILE_NAME_SUFFIX);
				System.out.println("Second database location: " + uncompressedDatabaseFile.getAbsolutePath());
				Assert.assertTrue(uncompressedDatabaseFile.exists());
				
				try (FullDataSourceV2Repo uncompressedRepo = new FullDataSourceV2Repo("jdbc:sqlite", uncompressedDatabaseFile))
				{
					FullDataSourceV2 uncompressedDataSource = FullDataSourceV2.createEmpty(0);
					
					int mappingEntryId1 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("1"), new TestBlockStateWrapper());
					int mappingEntryId2 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("2"), new TestBlockStateWrapper());
					
					uncompressedDataSource.setSingleColumn(
							new LongArrayList(new long[]{
									FullDataPointUtil.encode(mappingEntryId1, 1, 0, (byte) 15, (byte) 15),
									FullDataPointUtil.encode(mappingEntryId2, 1, 1, (byte) 15, (byte) 15),
							}),
							0, 0,
							EDhApiWorldGenerationStep.FEATURES,
							EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS
					);
					
					uncompressedRepo.save(FullDataSourceV2DTO.CreateFromDataSource(uncompressedDataSource, EDhApiDataCompressionMode.UNCOMPRESSED));
					
					checksum2 = uncompressedRepo.getByKey(0L).dataChecksum;
				}
			}
			
			Assert.assertEquals(checksum1, checksum2);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
	@Test
	public void testExtraMappingEntries()
	{
		System.out.println("--- FullDataChecksumStabilityTest - Different mapping order ---");
		
		try
		{
			int checksum1, checksum2;
			
			{
				File uncompressedDatabaseFile = File.createTempFile(DB_FILE_NAME_PREFIX, DB_FILE_NAME_SUFFIX);
				System.out.println("First database location: " + uncompressedDatabaseFile.getAbsolutePath());
				Assert.assertTrue(uncompressedDatabaseFile.exists());
				
				try (FullDataSourceV2Repo uncompressedRepo = new FullDataSourceV2Repo("jdbc:sqlite", uncompressedDatabaseFile))
				{
					FullDataSourceV2 uncompressedDataSource = FullDataSourceV2.createEmpty(0);
					
					int mappingEntryId1 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("1"), new TestBlockStateWrapper());
					int mappingEntryId2 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("2"), new TestBlockStateWrapper());
					uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("3"), new TestBlockStateWrapper());
					
					uncompressedDataSource.setSingleColumn(
							new LongArrayList(new long[]{
									FullDataPointUtil.encode(mappingEntryId1, 1, 0, (byte) 15, (byte) 15),
									FullDataPointUtil.encode(mappingEntryId2, 1, 1, (byte) 15, (byte) 15),
							}),
							0, 0,
							EDhApiWorldGenerationStep.FEATURES,
							EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS
					);
					
					uncompressedRepo.save(FullDataSourceV2DTO.CreateFromDataSource(uncompressedDataSource, EDhApiDataCompressionMode.UNCOMPRESSED));
					
					checksum1 = uncompressedRepo.getByKey(0L).dataChecksum;
				}
				
			}
			
			{
				File uncompressedDatabaseFile = File.createTempFile(DB_FILE_NAME_PREFIX, DB_FILE_NAME_SUFFIX);
				System.out.println("Second database location: " + uncompressedDatabaseFile.getAbsolutePath());
				Assert.assertTrue(uncompressedDatabaseFile.exists());
				
				try (FullDataSourceV2Repo uncompressedRepo = new FullDataSourceV2Repo("jdbc:sqlite", uncompressedDatabaseFile))
				{
					FullDataSourceV2 uncompressedDataSource = FullDataSourceV2.createEmpty(0);
					
					int mappingEntryId1 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("1"), new TestBlockStateWrapper());
					int mappingEntryId2 = uncompressedDataSource.mapping.addIfNotPresentAndGetId(new TestBiomeWrapper("2"), new TestBlockStateWrapper());
					
					uncompressedDataSource.setSingleColumn(
							new LongArrayList(new long[]{
									FullDataPointUtil.encode(mappingEntryId1, 1, 0, (byte) 15, (byte) 15),
									FullDataPointUtil.encode(mappingEntryId2, 1, 1, (byte) 15, (byte) 15),
							}),
							0, 0,
							EDhApiWorldGenerationStep.FEATURES,
							EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS
					);
					
					uncompressedRepo.save(FullDataSourceV2DTO.CreateFromDataSource(uncompressedDataSource, EDhApiDataCompressionMode.UNCOMPRESSED));
					
					checksum2 = uncompressedRepo.getByKey(0L).dataChecksum;
				}
			}
			
			Assert.assertEquals(checksum1, checksum2);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
	private static class TestBiomeWrapper implements IBiomeWrapper
	{
		private final String name;
		private TestBiomeWrapper(String nameSuffix)
		{
			this.name = "distanthorizons:testbiome" + nameSuffix;
		}
		
		
		@Override
		public String getName()
		{
			return this.name;
		}
		@Override
		public String getSerialString()
		{
			return this.name;
		}
		@Override
		public Object getWrappedMcObject()
		{
			return null;
		}
		
	}
	
	private static class TestBlockStateWrapper implements IBlockStateWrapper
	{
		@Override
		public String getSerialString()
		{
			return "BLOCKSTATE";
		}
		@Override
		public int getOpacity()
		{
			return 0;
		}
		@Override
		public int getLightEmission()
		{
			return 0;
		}
		@Override
		public byte getMaterialId()
		{
			return 0;
		}
		@Override
		public boolean isBeaconBlock()
		{
			return false;
		}
		@Override
		public boolean isBeaconTintBlock()
		{
			return false;
		}
		@Override
		public boolean isBeaconBaseBlock()
		{
			return false;
		}
		@Override
		public Color getMapColor()
		{
			return null;
		}
		@Override
		public Color getBeaconTintColor()
		{
			return null;
		}
		@Override
		public boolean isAir()
		{
			return false;
		}
		@Override
		public boolean isSolid()
		{
			return true;
		}
		@Override
		public boolean isLiquid()
		{
			return false;
		}
		@Override
		public Object getWrappedMcObject()
		{
			return null;
		}
		
	}
	
}
