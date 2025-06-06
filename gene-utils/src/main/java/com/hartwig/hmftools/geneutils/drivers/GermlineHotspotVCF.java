package com.hartwig.hmftools.geneutils.drivers;

import static com.hartwig.hmftools.geneutils.common.CommonUtils.GU_LOGGER;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.pathogenic.PathogenicSummaryFactory;
import com.hartwig.hmftools.common.pathogenic.Pathogenicity;
import com.hartwig.hmftools.common.variant.VcfFileReader;

import com.google.common.collect.Lists;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.VariantContextComparator;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

public final class GermlineHotspotVCF
{
    private static final String WHITELIST_FLAG = "WHITELIST";
    private static final String REF_GENOME_37 = "GRCh37";
    private static final String REF_GENOME_38 = "GRCh38";

    public static void write(
            final RefGenomeVersion refGenomeVersion, final String inputFile, final String outputFile, final List<String> genes) throws IOException
    {
        //VCFFileReader reader = new VCFFileReader(new File(inputFile), false);
        VcfFileReader reader = new VcfFileReader(inputFile);

        if(!reader.fileValid())
        {
            GU_LOGGER.error("clinvar file({}) invalid", inputFile);
            throw new IllegalArgumentException();
        }

        final VCFHeader readerHeader = reader.vcfHeader();
        final String assembly = readerHeader.getMetaDataLine("reference").getValue();

        if((refGenomeVersion == RefGenomeVersion.V37 && !assembly.equals(REF_GENOME_37))
        || (refGenomeVersion == RefGenomeVersion.V38 && !assembly.equals(REF_GENOME_38)))
        {
            GU_LOGGER.error("clinvar file({}) has incorrect ref genome version({}) vs required({})",
                    inputFile, assembly, refGenomeVersion);

            throw new IllegalArgumentException();
        }

        final List<VariantContext> variants = Lists.newArrayList();

        for(VariantContext context : reader.iterator())
        {
            final Set<String> variantGenes = Sets.newHashSet();
            final String geneInfo = context.getAttributeAsString("GENEINFO", "UNKNOWN:0000");
            for(String singleGeneInfo : geneInfo.split("\\|"))
            {
                variantGenes.add(singleGeneInfo.split(":")[0]);
            }

            // Intersection of variant and approved genes
            variantGenes.retainAll(genes);

            Pathogenicity pathogenicity = PathogenicSummaryFactory.fromContext(context).Status;
            if(!variantGenes.isEmpty() && isPathogenicOrLikelyPathogenic(pathogenicity) && context.getAlleles().size() == 2)
            {
                final String clinSigConf = PathogenicSummaryFactory.clnSigConf(context);
                String chromosome = refGenomeVersion.versionedChromosome(context.getContig());

                VariantContextBuilder builder = new VariantContextBuilder(
                        "clinvar", chromosome, context.getStart(), context.getEnd(),
                        context.getAlleles()).attribute("GENEINFO", geneInfo)
                        .attribute(PathogenicSummaryFactory.CLNSIG, PathogenicSummaryFactory.clnSig(context));

                if(!clinSigConf.isEmpty())
                {
                    builder.attribute(PathogenicSummaryFactory.CLNSIGCONF, clinSigConf);
                }

                variants.add(builder.make());
            }
        }

        reader.close();

        // ADD WHITE LIST (OUT OF ORDER)
        variants.addAll(assembly.equals(REF_GENOME_37) ? GermlineResources.whitelist37() : GermlineResources.whitelist38());

        // Get sorted contigs
        final List<String> contigs = variants.stream().map(VariantContext::getContig).distinct().collect(Collectors.toList());

        // WRITE
        VariantContextWriter writer = new VariantContextWriterBuilder().setOutputFile(outputFile)
                .modifyOption(Options.INDEX_ON_THE_FLY, true)
                .modifyOption(Options.USE_ASYNC_IO, false)
                .build();

        VCFHeader writerHeader = new VCFHeader();
        writerHeader.addMetaDataLine(readerHeader.getInfoHeaderLine("GENEINFO"));
        writerHeader.addMetaDataLine(readerHeader.getInfoHeaderLine(PathogenicSummaryFactory.CLNSIG));
        writerHeader.addMetaDataLine(readerHeader.getInfoHeaderLine(PathogenicSummaryFactory.CLNSIGCONF));
        writerHeader.addMetaDataLine(new VCFInfoHeaderLine(WHITELIST_FLAG, 1, VCFHeaderLineType.Flag, "Whitelisted hotspot"));
        writer.writeHeader(writerHeader);

        variants.sort(new VariantContextComparator(contigs));
        variants.forEach(writer::add);
        writer.close();
    }

    private static boolean isPathogenicOrLikelyPathogenic(final Pathogenicity pathogenicity)
    {
        return pathogenicity.isPathogenic();
    }
}
