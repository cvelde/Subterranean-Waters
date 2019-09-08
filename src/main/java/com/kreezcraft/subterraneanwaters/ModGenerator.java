package com.kreezcraft.subterraneanwaters;

import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.ArrayUtils;

import com.kreezcraft.subterraneanwaters.ModConfig.OceanConfig;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import com.kreezcraft.subterraneanwaters.util.OpenSimplexNoise;

public class ModGenerator {
    private static final IBlockState WATER = Blocks.WATER.getDefaultState();
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private boolean initialized = false;

    private Random random;
    private OpenSimplexNoise noiseMap;

    static OceanConfig config;

    public void generate(ChunkPrimer primer, World world, int chunkX, int chunkZ, String biomeName) {
        //TODO Move this to setup function?
        if (ArrayUtils.contains(ModConfig.dimensions, world.provider.getDimension())) {
            config = ModConfig.map.get(world.provider.getDimension());
            if (!initialized) {
                noiseMap = new OpenSimplexNoise(world.getSeed()); //generate noiseMap
                initialized = true;
            }

            doGenerate(chunkX, chunkZ, primer, biomeName);
        }
    }

    public double ridgenoise(double nx, double ny) {
        return 2 * (0.6 - Math.abs(0.6 - noiseMap.eval(nx, ny)));
    }

    private void doGenerate(int x, int z, ChunkPrimer primer, String biomeName) {//Actually generate the Cave

        double cutoff = 0.1; //Our cutoff value to clamp down the noise

        //Generate Underground Rivers
        for (int xShift = 0; xShift < 16; xShift++) { //Loop through all X coordinates in the chunk (0-16)
            for (int zShift = 0; zShift < 16; zShift++) { //Loop through all Z coordinates in the chunk (0-16)
                double xPos = (x * 16) + xShift; //Add our chunk position to the relative shift
                double zPos = (z * 16) + zShift; //Add our chunk position to the relative shift

                //select smaller part of noise
                xPos = xPos / 8 / 16;
                zPos = zPos / 8 / 16;

                //Blend different noise frequencies
                double e0 = 1 * ridgenoise(1 * xPos, 1 * zPos);
                double e1 = 0.5 * ridgenoise(2 * xPos, 2 * zPos) * e0;
                double e2 = 0.25 * ridgenoise(4 * xPos, 4 * zPos) * (e0 + e1);
                double e = e0 + e1 + e2;
                //IMPORTANT Don't actually use the xzPos coordinates after this anymore since we are working relative to the chunk

                double current = Math.pow(e, 2); //Ridge Factor TODO Put in Config maybe? (lower than 2 seems pointless)
                //double current = Math.round(e*2)/2; //Terrace Factor (more is less terraces)

                double trim = 0.5; //Todo Put in Config - Cuts off the Edges
                current -= trim;

                int multiplier = 16; //Todo Put in Config (24 puts occasional (but quite epic) holes into the surface)

                int yHeight = (int) (current * (multiplier / 2)); //change range to -1 to 1 (to restore the scale) multiply by half our desired cave height (goes in both directions)

                //DEBUG FUNCTION TODO Make config Option so I can leave this here for myself
                if (yHeight > 0) { //don't place center line
                        //primer.setBlockState(xShift, 150 + yHeight, zShift, Blocks.STAINED_GLASS.getDefaultState());
                        //primer.setBlockState(xShift, 150 - yHeight, zShift, Blocks.STAINED_GLASS.getDefaultState()); //flip around for bottom of cave
                } else {
                    //primer.setBlockState(xShift, 150, zShift, Blocks.GLASS.getDefaultState()); //place center line as guide
                }

                int waterlevel = 12; //level the water is up to
                int bottomOut = 6; //Flattening the bottom from this down
                int centerLine = 14; //Level the Noise is normalized to

                int OceanClamp = 25; //Limit height in Oceans (And DeepOceans) so as to not punch so many holes into the ocean floor
                String[] OceanBiomes = {"deep_ocean", "ocean", "beach"}; //List of Biomes to Clamp down on TODO Move to config
                //TODO This Produces artifacts in the ceiling of the cavern if and when a cavern crosses between allowed and not allowed biomes, not sure if this can be fixed.
                //Maybe the multiplier could be modified by the biomes instead of cutting the ceiling off


                for (int y = 0; y < yHeight; y++) { //height of generation
                    if (yHeight != 0) {//don't place center line or try to place under bottomOut
                        //generate above center part
                        if (centerLine + y < waterlevel){
                            primer.setBlockState(xShift, centerLine + y, zShift, Blocks.WATER.getDefaultState()); //place water
                        }
                        else if(ArrayUtils.contains(OceanBiomes, biomeName)) {
                            if (centerLine + y < OceanClamp)
                                primer.setBlockState(xShift, centerLine + y, zShift, Blocks.AIR.getDefaultState()); //place air
                        }
                        else {
                            primer.setBlockState(xShift, centerLine + y, zShift, Blocks.AIR.getDefaultState()); //place air
                        }
                        //generate below center part
                        if (centerLine - y < waterlevel && centerLine - y > bottomOut) {
                            primer.setBlockState(xShift, centerLine - y, zShift, Blocks.WATER.getDefaultState()); //place water
                        }
                        else if(centerLine - y > bottomOut) {
                            primer.setBlockState(xShift, centerLine - y, zShift, Blocks.AIR.getDefaultState()); //place air
                        }
                    }
                }
            }
        }
		/*
	//TODO Refactor this for optional rivers connecting the caverns (somewhat)
    //Old / For Later / Reference
		for(int xShift = 0;xShift< 16;xShift++) { //Loop through all X coordinates in the chunk (0-16)
        for (int zShift = 0; zShift < 16; zShift++) { //Loop through all Z coordinates in the chunk (0-16)
            double xPos = (x * 16) + xShift; //Add our chunk position to the relative shift
            double zPos = (z * 16) + zShift; //Add our chunk position to the relative shift
            double current = noiseMap.eval(xPos / 20 / 16, zPos / 20 / 16); //Our current Noise Level (-1 to 1)
            //IMPORTANT Don't actually use the xzPos coordinates after this anymore since we are working relative to the chunk


            if (current > -cutoff && current < cutoff) {//Ignore most of the Noise and only work in a designated set
                int yHeight = (int) ((current * (1 / cutoff)) * (48 / 2)); //change range to -1 to 1 (to restore the scale) multiply by half our desired cave height (goes in both directions)
                //DEBUG FUNCTION
                //primer.setBlockState(xShift, 200+yHeight, zShift, Blocks.GLASS.getDefaultState());

                for (int y = 0; y < yHeight; y++) {
                    if (y < 12)
                        primer.setBlockState(xShift, y, zShift, Blocks.WATER.getDefaultState());
                    else
                        primer.setBlockState(xShift, y, zShift, Blocks.AIR.getDefaultState());
                }
            }
        }

		 */
    }
}
