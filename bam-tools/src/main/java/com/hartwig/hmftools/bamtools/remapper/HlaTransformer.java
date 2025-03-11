package com.hartwig.hmftools.bamtools.remapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hartwig.hmftools.common.bam.SamRecordUtils;

import htsjdk.samtools.SAMRecord;

public class HlaTransformer
{

    private static final String HLA_PREFIX = "hla-";

    static boolean hasSomeHlaReference(SAMRecord record)
    {
        return hasAltReference(record) || mateHasAltReference(record);
    }

    static boolean hasAltReference(SAMRecord record)
    {
        return isHlaAltReference(record.getReferenceName());
    }

    static boolean mateHasAltReference(SAMRecord record)
    {
        return isHlaAltReference(record.getMateReferenceName());
    }

    static boolean isHlaAltReference(String referenceName)
    {
        return referenceName.toLowerCase().startsWith(HLA_PREFIX);
    }

    private final HlaRecordAligner mAligner;
    private final Map<String, SAMRecord> mRecordsByName = new HashMap<>();
    private int mNumberProcessed = 0;

    public HlaTransformer(final HlaRecordAligner aligner)
    {
        mAligner = aligner;
    }

    public List<SAMRecord> process(final SAMRecord record)
    {
        if(!hasSomeHlaReference(record))
        {
            return List.of(record);
        }

        if(record.isSecondaryOrSupplementary())
        {
            return List.of();
        }
        mNumberProcessed++;
        if(mRecordsByName.containsKey(record.getReadName()))
        {
            SAMRecord match = mRecordsByName.remove(record.getReadName());
            RecordPair pair = pair(match, record);
            return mAligner.alignPair(pair);
        }
        else
        {
            mRecordsByName.put(record.getReadName(), record);
            return List.of();
        }
    }

    public List<SAMRecord> unmatchedRecords()
    {
        return new ArrayList<>(mRecordsByName.values());
    }

    public int numberOfHlaRecordsProcessed()
    {
        return mNumberProcessed;
    }

    private RecordPair pair(final SAMRecord s, final SAMRecord r)
    {
        if(SamRecordUtils.firstInPair(s))
        {
            return new RecordPair(s, r);
        }
        return new RecordPair(r, s);
    }
}
