package com.hartwig.hmftools.peach.output;

import static com.hartwig.hmftools.common.peach.PeachUtil.DRUG_SEPARATOR;
import static com.hartwig.hmftools.common.peach.PeachUtil.UNKNOWN_ALLELE_STRING;
import static com.hartwig.hmftools.peach.PeachUtils.GERMLINE_TOTAL_COPY_NUMBER;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hartwig.hmftools.common.peach.ImmutablePeachGenotype;
import com.hartwig.hmftools.common.peach.PeachGenotype;
import com.hartwig.hmftools.peach.HaplotypeAnalysis;
import com.hartwig.hmftools.peach.effect.DrugInfoStore;
import com.hartwig.hmftools.peach.effect.HaplotypeFunctionStore;
import com.hartwig.hmftools.peach.haplotype.HaplotypeCombination;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.Nullable;

public class PeachGenotypeExtractor
{
    public static List<PeachGenotype> extract(
            final Map<String, HaplotypeAnalysis> geneToHaplotypeAnalysis,
            final @Nullable DrugInfoStore drugInfoStore, @Nullable HaplotypeFunctionStore haplotypeFunctionStore)
    {
        return geneToHaplotypeAnalysis.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> extractPeachGenotypesForGene(e.getKey(), e.getValue(), drugInfoStore, haplotypeFunctionStore))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private static List<PeachGenotype> extractPeachGenotypesForGene(
            final String gene, final HaplotypeAnalysis analysis,
            @Nullable DrugInfoStore drugInfoStore, @Nullable HaplotypeFunctionStore haplotypeFunctionStore)
    {
        HaplotypeCombination bestHaplotypeCombination = analysis.getBestHaplotypeCombination();
        if(bestHaplotypeCombination != null)
        {
            return bestHaplotypeCombination
                    .getHaplotypeNameToCount()
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> convertToPeachGenotype(gene, e.getKey(), e.getValue(), drugInfoStore, haplotypeFunctionStore))
                    .collect(Collectors.toList());
        }
        else
        {
            return List.of(convertToPeachGenotype(gene, UNKNOWN_ALLELE_STRING, GERMLINE_TOTAL_COPY_NUMBER, drugInfoStore, haplotypeFunctionStore));
        }
    }

    private static PeachGenotype convertToPeachGenotype(
            final String gene, final String haplotypeName, int count,
            @Nullable DrugInfoStore drugInfoStore, @Nullable HaplotypeFunctionStore haplotypeFunctionStore)
    {
        String printableHaplotypeFunction = getPrintableHaplotypeFunction(gene, haplotypeName, haplotypeFunctionStore);
        String printableDrugNamesString = getPrintableDrugNamesString(gene, drugInfoStore);
        String printablePresciptionUrlsString = getPrintablePresciptionUrlsString(gene, drugInfoStore);

        return ImmutablePeachGenotype.builder()
                .gene(gene)
                .allele(haplotypeName)
                .alleleCount(count)
                .function(printableHaplotypeFunction)
                .linkedDrugs(printableDrugNamesString)
                .urlPrescriptionInfo(printablePresciptionUrlsString)
                .build();
    }

    private static String getPrintableHaplotypeFunction(
            final String gene, final String haplotypeName,
            @Nullable HaplotypeFunctionStore haplotypeFunctionStore)
    {
        if(haplotypeFunctionStore == null)
        {
            return Strings.EMPTY;
        }
        String functionality = haplotypeFunctionStore.getFunction(gene, haplotypeName);
        return Objects.requireNonNullElse(functionality, Strings.EMPTY);
    }

    private static String getPrintableDrugNamesString(final String gene, @Nullable DrugInfoStore drugInfoStore)
    {
        if(drugInfoStore == null)
        {
            return Strings.EMPTY;
        }
        List<String> sortedLinkedDrugNames = getSortedLinkedDrugNames(gene, drugInfoStore);
        if(sortedLinkedDrugNames.isEmpty())
        {
            return Strings.EMPTY;
        }
        else
        {
            return String.join(DRUG_SEPARATOR, sortedLinkedDrugNames);
        }
    }

    private static String getPrintablePresciptionUrlsString(final String gene, @Nullable DrugInfoStore drugInfoStore)
    {
        if(drugInfoStore == null)
        {
            return Strings.EMPTY;
        }
        List<String> sortedLinkedDrugNames = getSortedLinkedDrugNames(gene, drugInfoStore);
        if(sortedLinkedDrugNames.isEmpty())
        {
            return Strings.EMPTY;
        }
        else
        {
            return sortedLinkedDrugNames.stream()
                    .map(d -> drugInfoStore.getPrescriptionInfoUrl(gene, d))
                    .collect(Collectors.joining(DRUG_SEPARATOR));
        }
    }

    private static List<String> getSortedLinkedDrugNames(final String gene, final DrugInfoStore drugInfoStore)
    {
        return drugInfoStore.getRelevantDrugNames(gene).stream().sorted().collect(Collectors.toList());
    }
}
