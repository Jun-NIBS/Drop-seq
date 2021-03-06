/*
 * The Broad Institute
 * SOFTWARE COPYRIGHT NOTICE AGREEMENT
 * This software and its documentation are copyright 2017 by the
 * Broad Institute/Massachusetts Institute of Technology. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever.
 * Neither the Broad Institute nor MIT can be responsible for its use, misuse,
 * or functionality.
 */
package org.broadinstitute.dropseqrna.utils;

import htsjdk.samtools.SAMRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds if specified potions of a read are below a minimum base quality.
 * @author nemesh
 *
 */
public class BaseQualityFilter {

	private final List<BaseRange> baseRanges;
	private final int baseQualityThrehsold;
	private FailedBaseMetric metric = null;

	public BaseQualityFilter (final List<BaseRange> baseRanges, final int baseQualityThrehsold) {
		this.baseQualityThrehsold=baseQualityThrehsold;
		this.baseRanges=baseRanges;
		this.metric = new FailedBaseMetric(BaseRange.getTotalRangeSize(baseRanges));
	}

	public int scoreBaseQuality(final SAMRecord barcodedRead) {
		int numBasesBelowQuality=0;
		byte [] qual= barcodedRead.getBaseQualities();
		char [] seq = barcodedRead.getReadString().toUpperCase().toCharArray();
		for (BaseRange b: baseRanges)
			for (int i=b.getStart()-1; i<b.getEnd(); i++) {
				byte q = qual[i];
				char s = seq[i];

				if (q < this.baseQualityThrehsold || s=='N')
					numBasesBelowQuality++;
			}

		this.metric.addFailedBase(numBasesBelowQuality);
		return (numBasesBelowQuality);
	}

	public FailedBaseMetric getMetric () {
		return this.metric;
	}

	public List<BaseRange> getBaseRanges () {
		return this.baseRanges;
	}

	public int getBaseQualityThreshold () {
		return this.baseQualityThrehsold;
	}

	public class FailedBaseMetric {
		List<Integer> data = null;

		public FailedBaseMetric (final Integer length){
			data=new ArrayList<>(length+1);
			for (int i=0; i<=length; i++)
				data.add(new Integer(0));
		}

		public void addFailedBase(final int numBasesFailed) {
			Integer i = data.get(numBasesFailed);
			i++;
			data.set(numBasesFailed, i);
		}

		public int getNumFailedBases(final int position) {
			return (data.get(position));
		}

		public int getLength() {
			return (data.size());
		}

	}


}
