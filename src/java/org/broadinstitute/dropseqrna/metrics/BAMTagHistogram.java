package org.broadinstitute.dropseqrna.metrics;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.*;

import java.io.File;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.broadinstitute.dropseqrna.cmdline.DropSeq;
import org.broadinstitute.dropseqrna.utils.ObjectCounter;

import picard.cmdline.CommandLineProgram;
import picard.cmdline.CommandLineProgramProperties;
import picard.cmdline.Option;
import picard.cmdline.StandardOptionDefinitions;

/**
 * For a bam file, generate the histogram of values for a particular BAM tag.
 * @author nemesh
 *
 */
@CommandLineProgramProperties(usage = "Create a histogram of values for the given tag",
        usageShort = "Create a histogram of values for the given tag",
        programGroup = DropSeq.class)
public class BAMTagHistogram extends CommandLineProgram {

	private static final Log log = Log.getInstance(BAMTagHistogram.class);
	
	@Option(shortName = StandardOptionDefinitions.INPUT_SHORT_NAME, doc = "The input SAM or BAM file to analyze.  Must be coordinate sorted. (???)")
	public File INPUT;
	
	@Option(shortName = StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc="Output file of histogram of tag value frequencies. This supports zipped formats like gz and bz2.")
	public File OUTPUT;
	
	@Option(doc="Tag to extract")
	public String TAG;
	
	@Option(doc="Filter PCR Duplicates.")
	public boolean FILTER_PCR_DUPLICATES=false;
	
	@Option(doc="Read quality filter.  Filters all reads lower than this mapping quality.  Defaults to 10.  Set to 0 to not filter reads by map quality.")
	public Integer READ_QUALITY=10;
	
	@Override
	protected int doWork() {
		IOUtil.assertFileIsReadable(INPUT);
		IOUtil.assertFileIsWritable(OUTPUT);
		
		ObjectCounter<String> counter=getBamTagCounts(INPUT, this.TAG, this.READ_QUALITY, this.FILTER_PCR_DUPLICATES);
		List<String> tagsByCount=counter.getKeysOrderedByCount(true);
		
		PrintStream writer = new PrintStream(IOUtil.openFileForWriting(OUTPUT));
		
		for (String s: tagsByCount) {
			int count = counter.getCountForKey(s);
			String [] h={count+"", s};
			String result = StringUtils.join(h, "\t");
			writer.println(result);
		}
		writer.close();
		return 0;
	}
	
	public ObjectCounter<String> getBamTagCounts (File bamFile, String tag, int readQuality, boolean filterPCRDuplicates) {
		SamReader inputSam = SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.EAGERLY_DECODE).open(bamFile);
        try {
            return getBamTagCounts(inputSam.iterator(), tag, readQuality, filterPCRDuplicates);
        } finally {
            CloserUtil.close(inputSam);
        }
    }

    public ObjectCounter<String> getBamTagCounts (Iterator<SAMRecord> iterator, String tag, int readQuality, boolean filterPCRDuplicates) {
        ProgressLogger pl = new ProgressLogger(log);

        ObjectCounter<String> counter = new ObjectCounter<String>();

        for (final SAMRecord r : new IterableAdapter<>(iterator)) {
            pl.record(r);
            if (filterPCRDuplicates && r.getDuplicateReadFlag()) continue;
            if (r.getMappingQuality()<readQuality) continue;
            if (r.isSecondaryOrSupplementary()) continue;
            //String s1 = r.getStringAttribute(tag);
            String s1 = getAnyTagAsString(r, tag);
            if (s1!=null && s1!="") counter.increment(s1); // if the tag doesn't have a value, don't increment it.

        }
        return (counter);
    }
    
	
	public String getAnyTagAsString (SAMRecord r, String tag) {
		String s = null;
		Object o = r.getAttribute(tag);
		if (o==null) return (s);
		if (o instanceof String) {
			s = (String) o;
			return (s);
		}
		if (o instanceof Integer) {
			Integer i = (Integer) o;
			s= i.toString();
			return (s);
		}
		return (s);
		
	}
	
	
	
	
	/** Stock main method. */
	public static void main(final String[] args) {
		System.exit(new BAMTagHistogram().instanceMain(args));
	}
	
	/*
	private class BAMTagMetric extends MetricBase {
		Histogram <String>histogram = new Histogram<String>("tag", "count");
		
		public Histogram<String> getHistogram() {
			return this.histogram;
		}
		
		
	}
	*/
	
	
}