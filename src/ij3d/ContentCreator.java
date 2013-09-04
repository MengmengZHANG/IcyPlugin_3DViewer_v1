package ij3d;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.sequence.SequenceUtil;
//import ij.ImagePlus;
//import ij.ImageStack;
////Mengmeng
////import ij.ImagePlus;
//import ij.IJ;
//import ij.io.FileInfo;
//import ij.process.ColorProcessor;
//import ij.process.ImageConverter;
//import ij.process.StackConverter;

import java.io.File;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;

//import customnode.CustomMesh;
//import customnode.CustomMultiMesh;
//import customnode.CustomMeshNode;

import javax.vecmath.Color3f;

import voltex.VoltexGroup;
import voltex.VoltexVolume;

public class ContentCreator {

	private static final boolean SWAP_TIMELAPSE_DATA = false;

	/**
	 * @author Mengmeng
	 * @param  sequences Sequence[]
	 */
	public static Content createContent(ContentFlag contentFlag,
			Sequence sequence, int type, int resf, int tp, Color3f color,
			int thresh, boolean[] channels) {

		ContentInstant content = new ContentInstant(
				contentFlag.getContentName());
		Sequence sequenceInstant = SequenceUtil.getSubSequence(sequence, 0, 0,
				contentFlag.getPosC(), 0, contentFlag.getPosT(),
				sequence.getSizeX(), sequence.getSizeY(), 1,
				sequence.getSizeZ(), 1) ;
		content.setSequence(sequenceInstant);
		content.setContentFlag(contentFlag) ;
		content.color = color;
		content.threshold = thresh;
		content.channels = channels;
		content.setResamplingF(resf);
		content.setTimepoint(tp); // old

		content.displayAs(type);
		
		content.compile();

		 TreeMap<Integer, ContentInstant> instants =
				 new TreeMap<Integer, ContentInstant>();
		instants.put(0, content) ;
		Content c = new Content(contentFlag.getContentName(), instants) ;
		
		int[] rLUT = new int[256];
		int[] gLUT = new int[256];
		int[] bLUT = new int[256];
		int[] aLUT = new int[256];
		
		VoltexGroup voltexGroup = (VoltexGroup) content.getContent();
		VoltexVolume volume = voltexGroup.getRenderer().getVolume();
		
		if (content.getContentFlag().getSizeC() != 1 && content.getPosC() == 0) {
			volume.getRedLUT(rLUT);
		}else if (content.getContentFlag().getSizeC() != 1 && content.getPosC() == 1) {
			volume.getGreenLUT(gLUT);
		}else if (content.getContentFlag().getSizeC() != 1 && content.getPosC() == 2) {
			volume.getBlueLUT(bLUT);
		}else{
			//use grey LUT
			volume.getRedLUT(rLUT);
			volume.getGreenLUT(gLUT);
			volume.getBlueLUT(bLUT);
		}
		volume.getAlphaLUT(aLUT);
		c.setLUT(rLUT, gLUT, bLUT, aLUT);
		
		return c;
	}
}
