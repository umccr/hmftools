package com.hartwig.hmftools.esvee.assembly.output;

import static java.lang.Math.abs;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.sv.LineElements.isMobileLineElement;
import static com.hartwig.hmftools.common.utils.file.CommonFields.FLD_CHROMOSOME;
import static com.hartwig.hmftools.common.utils.file.CommonFields.FLD_ORIENTATION;
import static com.hartwig.hmftools.common.utils.file.CommonFields.FLD_POSITION;
import static com.hartwig.hmftools.common.utils.file.FileDelimiters.ITEM_DELIM;
import static com.hartwig.hmftools.common.utils.file.FileDelimiters.TSV_DELIM;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.esvee.assembly.AssemblyConfig.SV_LOGGER;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hartwig.hmftools.esvee.assembly.AssemblyConfig;
import com.hartwig.hmftools.esvee.assembly.alignment.AlternativeAlignment;
import com.hartwig.hmftools.esvee.assembly.alignment.AssemblyAlignment;
import com.hartwig.hmftools.esvee.assembly.alignment.Breakend;
import com.hartwig.hmftools.esvee.assembly.alignment.BreakendSegment;
import com.hartwig.hmftools.esvee.assembly.types.Junction;
import com.hartwig.hmftools.esvee.assembly.types.JunctionAssembly;

public class BreakendWriter
{
    private final AssemblyConfig mConfig;

    private final BufferedWriter mWriter;

    public BreakendWriter(final AssemblyConfig config)
    {
        mConfig = config;

        mWriter = initialiseWriter();
    }

    public void close() { closeBufferedWriter(mWriter);}

    public static String FLD_BREAKEND_INS_SEQ = "InsertedBases";
    public static String FLD_BREAKEND_MATE_CHR = "MateChr";
    public static String FLD_BREAKEND_MATE_POSITION = "MatePos";
    public static String FLD_BREAKEND_MATE_ORIENT = "MateOrient";
    public static String FLD_SV_TYPE = "Type";

    private BufferedWriter initialiseWriter()
    {
        if(!mConfig.WriteTypes.contains(WriteType.BREAKEND))
            return null;

        try
        {
            BufferedWriter writer = createBufferedWriter(mConfig.outputFilename(WriteType.BREAKEND));

            StringJoiner sj = new StringJoiner(TSV_DELIM);

            sj.add("Id");
            sj.add("PhaseGroupId");
            sj.add("PhaseSetId");
            sj.add("AssemblyId");
            sj.add("MateId");
            sj.add("AssemblyInfo");

            sj.add(FLD_SV_TYPE).add(FLD_CHROMOSOME).add(FLD_POSITION).add(FLD_ORIENTATION);

            sj.add(FLD_BREAKEND_MATE_CHR).add(FLD_BREAKEND_MATE_POSITION).add(FLD_BREAKEND_MATE_ORIENT).add("Length");
            sj.add(FLD_BREAKEND_INS_SEQ).add("Homology").add("ConfidenceInterval").add("InexactOffset");

            sj.add("Qual");
            sj.add("SplitFragments");
            sj.add("RefSplitFragments");
            sj.add("DiscFragments");
            sj.add("RefDiscFragments");
            sj.add("ForwardReads");
            sj.add("ReverseReads");

            sj.add("SequenceLength");
            sj.add("SegmentCount");
            sj.add("SegmentIndex");
            sj.add("SequenceIndex");
            sj.add("AlignedBases");
            sj.add("MapQual");
            sj.add("Score");
            sj.add("AdjAlignedBases");
            sj.add("AvgFragmentLength");
            sj.add("IncompleteFragments");
            sj.add("BreakendQual");

            sj.add("FacingBreakendIds");

            sj.add("AltAlignments");
            sj.add("InsertionType");
            sj.add("UniqueFragPos");
            sj.add("ClosestAssembly");

            writer.write(sj.toString());
            writer.newLine();

            return writer;
        }
        catch(IOException e)
        {
            SV_LOGGER.error("failed to initialise breakend writer: {}", e.toString());
            return null;
        }
    }

    public void writeBreakends(final AssemblyAlignment assemblyAlignment)
    {
        if(mWriter == null)
            return;

        try
        {
            String assemblyInfo = assemblyAlignment.info();

            for(Breakend breakend : assemblyAlignment.breakends())
            {
                StringJoiner sj = new StringJoiner(TSV_DELIM);

                sj.add(String.valueOf(breakend.id()));
                sj.add(String.valueOf(assemblyAlignment.assemblies().get(0).phaseGroup().id()));
                sj.add(String.valueOf(assemblyAlignment.phaseSet() != null ? assemblyAlignment.phaseSet().id() : -1));
                sj.add(String.valueOf(assemblyAlignment.id()));
                sj.add(!breakend.isSingle() ? String.valueOf(breakend.otherBreakend().id()) : "");
                sj.add(assemblyInfo);
                sj.add(String.valueOf(breakend.svType()));
                sj.add(breakend.Chromosome);
                sj.add(String.valueOf(breakend.Position));
                sj.add(String.valueOf(breakend.Orient));

                if(breakend.otherBreakend() != null)
                {
                    sj.add(breakend.otherBreakend().Chromosome);
                    sj.add(String.valueOf(breakend.otherBreakend().Position));
                    sj.add(String.valueOf(breakend.otherBreakend().Orient));
                    sj.add(String.valueOf(breakend.svLength()));

                }
                else
                {
                    sj.add("").add("").add("").add("0");
                }

                sj.add(breakend.InsertedBases);

                if(breakend.Homology != null)
                {
                    sj.add(breakend.Homology.Homology);
                    sj.add(format("%d,%d", breakend.Homology.ExactStart, breakend.Homology.ExactEnd));
                    sj.add(format("%d,%d", breakend.Homology.InexactStart, breakend.Homology.InexactEnd));
                }
                else
                {
                    sj.add("").add("0,0").add("0,0");
                }

                sj.add(String.valueOf(breakend.calcSvQual()));

                int tumorCount = mConfig.TumorIds.size();

                int splitFrags = 0;
                int refSplitFrags = 0;
                int discFrags = 0;
                int refDiscFrags = 0;
                int forwardReads = 0;
                int reverseReads = 0;

                for(int i = 0; i < breakend.sampleSupport().size(); ++i)
                {
                    splitFrags += breakend.sampleSupport().get(i).SplitFragments;
                    discFrags += breakend.sampleSupport().get(i).DiscordantFragments;

                    if(i >= tumorCount)
                    {
                        refSplitFrags += breakend.sampleSupport().get(i).SplitFragments;
                        refDiscFrags += breakend.sampleSupport().get(i).DiscordantFragments;
                    }

                    forwardReads += breakend.sampleSupport().get(i).ForwardReads;
                    reverseReads += breakend.sampleSupport().get(i).ReverseReads;
                }

                sj.add(String.valueOf(splitFrags));
                sj.add(String.valueOf(refSplitFrags));
                sj.add(String.valueOf(discFrags));
                sj.add(String.valueOf(refDiscFrags));
                sj.add(String.valueOf(forwardReads));
                sj.add(String.valueOf(reverseReads));

                sj.add(String.valueOf(assemblyAlignment.fullSequenceLength()));

                // for now just the first segment - no showing branching or duplicates
                BreakendSegment segment = breakend.segments().get(0);
                sj.add(String.valueOf(breakend.segments().size()));
                sj.add(String.valueOf(segment.Index));
                sj.add(String.valueOf(segment.SequenceIndex));
                sj.add(String.valueOf(segment.Alignment.alignedBases()));
                sj.add(String.valueOf(segment.Alignment.mapQual()));
                sj.add(String.valueOf(segment.Alignment.score()));
                sj.add(String.valueOf(segment.Alignment.adjustedAlignment()));
                sj.add(String.valueOf(breakend.averageFragmentLength()));
                sj.add(String.valueOf(breakend.incompleteFragmentCount()));

                sj.add(String.valueOf(breakend.calcQual()));

                String facingBreakendIds = breakend.facingBreakends().stream().map(x -> String.valueOf(x.id())).collect(Collectors.joining(ITEM_DELIM));
                sj.add(facingBreakendIds);

                sj.add(AlternativeAlignment.toVcfTag(breakend.alternativeAlignments()));

                if(assemblyAlignment.assemblies().stream().anyMatch(x -> x.hasLineSequence())
                && isMobileLineElement(breakend.Orient, breakend.InsertedBases))
                {
                    sj.add("LINE"); // in time other types
                }
                else
                {
                    sj.add("NONE");
                }

                int[] uniqueFragPositions = breakend.uniqueFragmentPositionCounts();

                if(uniqueFragPositions != null)
                    sj.add(format("%d:%d", uniqueFragPositions[0], uniqueFragPositions[1]));
                else
                    sj.add("-1:-1");

                String assemblyMatchStr = "";

                for(JunctionAssembly assembly : assemblyAlignment.assemblies())
                {
                    Junction junction = assembly.junction();

                    if(junction.Chromosome.equals(breakend.Chromosome) && junction.Orient == breakend.Orient
                    && abs(junction.Position - breakend.Position) < 100)
                    {
                        assemblyMatchStr = junction.coordsTyped();
                    }
                }

                sj.add(assemblyMatchStr);

                mWriter.write(sj.toString());
                mWriter.newLine();
            }
        }
        catch(IOException e)
        {
            SV_LOGGER.error("failed to write breakends: {}", e.toString());
        }
    }
}
